# sprintmodus-auth-service

Authentication and tenant onboarding for Sprintmodus (port **8091**, reached only through the api-gateway).

- **Login** with `email + password + organizationCode`: the master DB resolves the organization and checks its
  subscription, then the user's credentials are checked in that organization's own tenant DB, and a JWT is issued.
- **Onboarding** (`register-organization`): creates the organization's `tenant_{tenantId}` database, runs the tenant
  Flyway scripts, creates the owner, and records the organization and a FREE subscription in the master DB.

The master DB holds no users. Every user lives in their tenant's database.

## Endpoints

| Method | Path | Notes |
|---|---|---|
| POST | `/auth/login` | `{organizationCode, email, password}` → `200 {token, user, organization, subscription}`. `401` for any bad org/user/password (identical response), `402` for an inactive or expired subscription |
| POST | `/auth/register-organization` | `{organizationName, organizationCode, fullName, email, password}` → `201`. `400` invalid data, `409` code or email taken |
| GET | `/auth/organization-code-available?code=` | `200 {available, reason?}`, same rules as registration |
| POST | `/auth/refresh-token` | `Authorization: Bearer <token>` → same body as login, with limits re-read from the DB. Expired tokens cannot be refreshed |
| GET | `/auth/validate-token` | `Authorization: Bearer <token>` → `200 {valid, userId, tenantId, ...}` or `401`. Signature and expiry only, no DB access |

Errors are `{code, message, timestamp}`. Everything else is closed. CORS is not configured here: the gateway is the only CORS authority.

## JWT

HS512. Claims: `sub` (user code), `email`, `tenantId`, `organizationCode`, `role` (`OWNER|ADMIN|MEMBER`), `plan`,
`maxProjects`, `maxUsers`, `maxStorageMB`, `iat`, `exp`, `iss`. There is deliberately no database host or name: the
backend derives the tenant database from `tenantId`.

## Running

Needs MySQL 9.7 with a `master_db` database (`docker compose up -d` in `sprintmodus-ng`), and Eureka on 8761.

```bash
./mvnw spring-boot:run     # activates the `dev` profile: development-only secrets, never deploy with it
```

Master migrations (from the `db-master` jar) run at startup. Tenant migrations (from `db-tenant`) run at onboarding.
Install `common-lib`, `db-master` and `db-tenant` first (`./mvnw install` in each).

### Configuration

| Property | Env var | Notes |
|---|---|---|
| `sprintmodus.jwt.secret` | `JWT_SECRET` | **Required**, at least 64 bytes. The service refuses to start without it |
| `sprintmodus.jwt.expiration` | | Default `PT1H` |
| `sprintmodus.tenant-credentials.encryption-password` / `.salt` | `TENANT_CREDENTIALS_KEY` / `TENANT_CREDENTIALS_SALT` | **Required**. Encrypts the tenant DB password recorded in the master DB (hex salt) |
| `spring.datasource.master.*` | `MASTER_DB_ROOT_PASSWORD` | Master DB connection |
| `spring.datasource.tenant.*` | `MASTER_DB_ROOT_PASSWORD` | Shared by all tenant DBs (one MySQL host) |

The database user needs `CREATE`/`DROP` on `tenant_%` databases, since onboarding creates them.

## Layout

Clean Architecture, dependencies point inward: `domain` (no framework) ← `application` (use cases returning
`Result<T, E>`, ports) ← `adapter` (REST, JWT) and `infrastructure` (Spring configuration, persistence).
Business errors are sealed `ApplicationError` types mapped to HTTP status in one place (`ErrorMapper`); only
infrastructure failures are exceptions (HTTP 500).

**Every query is native SQL.** JPA/Hibernate only maps results to entities and manages the two persistence units (master
and tenant). Repository interfaces are ports in `application/port/persistence`; everything else is in
`infrastructure/persistence` (`master/` and `tenant/`): the `@Entity` persistence models, the `*Queries` interfaces (they
extend the bare Spring Data `Repository` and every method is a hand-written `@Query(nativeQuery = true)`, reads and
`@Modifying` inserts/updates alike, with `UUID_TO_BIN()` for the `BINARY(16)` UUIDs), and the `Jpa*Repository`
implementations that map entities to domain objects. Registration writes the organization, its subscription and the audit
rows in one master-DB transaction. `ArchitectureRulesTest` fails the build on JPQL, derived queries, `save`/`persist`, or
persistence technology outside `infrastructure`.

## Tests

```bash
./mvnw verify
```

Unit tests use in-memory fakes for the ports. `AuthServiceIntegrationTest` starts `mysql:9.7` (Docker required) and
runs the whole service against it.

## Known limits

- One connection pool per active tenant is kept open for the life of the process, with no eviction. Fine for a
  handful of tenants; add an LRU/idle eviction before onboarding many.
- No rate limiting or lockout on `/auth/login` and `/auth/register-organization`. Add it at the gateway before exposing
  the service publicly.
- The tenant DB password is stored encrypted in `Organization`, but nothing reads it back yet: connections use the
  shared credentials from configuration (see `TenantDatabaseNameResolver`).

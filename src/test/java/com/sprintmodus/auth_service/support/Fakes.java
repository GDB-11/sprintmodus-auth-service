package com.sprintmodus.auth_service.support;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.sprintmodus.auth_service.application.dto.TokenClaims;
import com.sprintmodus.auth_service.application.error.TokenError;
import com.sprintmodus.auth_service.application.port.external.PasswordHasher;
import com.sprintmodus.auth_service.application.port.external.TenantProvisioner;
import com.sprintmodus.auth_service.application.port.external.TokenProvider;
import com.sprintmodus.auth_service.application.port.persistence.OrganizationRepository;
import com.sprintmodus.auth_service.application.port.persistence.SubscriptionRepository;
import com.sprintmodus.auth_service.application.port.persistence.TenantUserRepository;
import com.sprintmodus.auth_service.domain.model.Organization;
import com.sprintmodus.auth_service.domain.model.OrganizationRole;
import com.sprintmodus.auth_service.domain.model.OrganizationSubscription;
import com.sprintmodus.auth_service.domain.model.Plan;
import com.sprintmodus.auth_service.domain.model.TenantUser;
import com.sprintmodus.common_lib.result.Result;

/** In-memory implementations of the application ports, so use cases can be tested without Spring or a database. */
public final class Fakes {

	private Fakes() {
	}

	public static class Organizations implements OrganizationRepository {

		public final List<Organization> stored = new ArrayList<>();

		public final List<Registration> registrations = new ArrayList<>();

		/** When set, {@link #register} loses a race on this key instead of succeeding. */
		public Duplicate raceLostOn;

		private long nextId = 1;

		public Organization add(String code, UUID tenantId, boolean active) {
			Organization organization = new Organization(nextId++, tenantId, code, "Org " + code, code + "@example.com", active);
			stored.add(organization);
			return organization;
		}

		@Override
		public Optional<Organization> findByCode(String code) {
			return stored.stream().filter(organization -> organization.code().equalsIgnoreCase(code)).findFirst();
		}

		@Override
		public boolean existsByCode(String code) {
			return findByCode(code).isPresent();
		}

		@Override
		public boolean existsByEmail(String email) {
			return stored.stream().anyMatch(organization -> organization.email().equalsIgnoreCase(email));
		}

		@Override
		public Result<Organization, Duplicate> register(Registration registration) {
			if (raceLostOn != null) {
				return Result.failure(raceLostOn);
			}
			registrations.add(registration);
			Organization organization = new Organization(nextId++, registration.tenantId(), registration.code(),
					registration.name(), registration.email(), true);
			stored.add(organization);
			return Result.success(organization);
		}

	}

	public static class Subscriptions implements SubscriptionRepository {

		private final Map<Long, OrganizationSubscription> byOrganization = new HashMap<>();

		public void add(long organizationId, Plan plan, Instant expiresAt, boolean active) {
			byOrganization.put(organizationId, new OrganizationSubscription(organizationId, plan, plan.maxProjects(),
					plan.maxUsers(), plan.maxStorageMB(), expiresAt, active));
		}

		@Override
		public Optional<OrganizationSubscription> findByOrganizationId(long organizationId) {
			return Optional.ofNullable(byOrganization.get(organizationId));
		}

	}

	public static class TenantUsers implements TenantUserRepository {

		private final Map<UUID, List<TenantUser>> byTenant = new HashMap<>();

		public final List<UUID> lookedUpTenants = new ArrayList<>();

		public final Map<UUID, Instant> logins = new HashMap<>();

		public final List<NewUser> created = new ArrayList<>();

		/** When set, {@link #create} fails like a broken database would. */
		public RuntimeException failCreateWith;

		public TenantUser add(UUID tenantId, String email, String passwordHash, OrganizationRole role, boolean active) {
			TenantUser user = new TenantUser(UUID.randomUUID(), email, "User " + email, passwordHash, role, active);
			byTenant.computeIfAbsent(tenantId, _ -> new ArrayList<>()).add(user);
			return user;
		}

		@Override
		public Optional<TenantUser> findByEmail(UUID tenantId, String email) {
			lookedUpTenants.add(tenantId);
			return byTenant.getOrDefault(tenantId, List.of()).stream()
					.filter(user -> user.email().equalsIgnoreCase(email)).findFirst();
		}

		@Override
		public Optional<TenantUser> findByCode(UUID tenantId, UUID userCode) {
			lookedUpTenants.add(tenantId);
			return byTenant.getOrDefault(tenantId, List.of()).stream()
					.filter(user -> user.userCode().equals(userCode)).findFirst();
		}

		@Override
		public void recordLogin(UUID tenantId, UUID userCode, Instant at) {
			logins.put(userCode, at);
		}

		@Override
		public void create(UUID tenantId, NewUser user) {
			if (failCreateWith != null) {
				throw failCreateWith;
			}
			created.add(user);
		}

	}

	/** Hashes as {@code hashed:<password>} and counts comparisons, to check that timing does not leak. */
	public static class Hasher implements PasswordHasher {

		public int comparisons;

		@Override
		public String hash(String rawPassword) {
			return "hashed:" + rawPassword;
		}

		@Override
		public boolean matches(String rawPassword, String hash) {
			comparisons++;
			return hash.equals("hashed:" + rawPassword);
		}

	}

	/** Issues {@code token-for-<email>} and verifies only tokens it issued, or ones a test registers. */
	public static class Tokens implements TokenProvider {

		private final Map<String, Result<TokenClaims.Verified, TokenError>> known = new HashMap<>();

		public TokenClaims lastIssued;

		@Override
		public String generate(TokenClaims claims) {
			lastIssued = claims;
			String token = "token-for-" + claims.email();
			known.put(token, Result.success(new TokenClaims.Verified(claims, Instant.parse("2030-01-01T00:00:00Z"))));
			return token;
		}

		public void register(String token, Result<TokenClaims.Verified, TokenError> outcome) {
			known.put(token, outcome);
		}

		@Override
		public Result<TokenClaims.Verified, TokenError> verify(String token) {
			return known.getOrDefault(token, Result.failure(new TokenError.InvalidToken()));
		}

	}

	public static class Provisioner implements TenantProvisioner {

		public final List<UUID> created = new ArrayList<>();

		public final List<UUID> dropped = new ArrayList<>();

		public RuntimeException failCreateWith;

		@Override
		public TenantDatabase create(UUID tenantId) {
			if (failCreateWith != null) {
				throw failCreateWith;
			}
			created.add(tenantId);
			return new TenantDatabase("db.internal:3306", "tenant_" + tenantId.toString().replace("-", ""), "app",
					"encrypted-secret");
		}

		@Override
		public void drop(UUID tenantId) {
			dropped.add(tenantId);
		}

	}

}

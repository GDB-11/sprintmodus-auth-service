package com.sprintmodus.auth_service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.sprintmodus.auth_service.application.port.external.TenantProvisioner;

import tools.jackson.databind.json.JsonMapper;

/**
 * The whole service against a real MySQL 9.7: registration provisions a tenant database, login crosses the master and
 * tenant databases, and tenants stay isolated from each other.
 */
@Testcontainers
@SpringBootTest(properties = { "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false" })
@AutoConfigureMockMvc
class AuthServiceIntegrationTest {

	private static final String ROOT_PASSWORD = "test";

	private static final String PARAMS = "connectionTimeZone=UTC&allowPublicKeyRetrieval=true";

	private static final String PASSWORD = "correct-horse-battery";

	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Container
	static final GenericContainer<?> MYSQL = new GenericContainer<>("mysql:9.7")
			.withEnv("MYSQL_ROOT_PASSWORD", ROOT_PASSWORD)
			.withEnv("MYSQL_DATABASE", "master_db")
			.withExposedPorts(3306)
			// the entrypoint runs a temporary socket-only server first; only the final server logs port 3306
			.waitingFor(Wait.forLogMessage(".*mysqld: ready for connections.*port: 3306 .*", 1));

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.master.jdbc-url",
				() -> "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/master_db?" + PARAMS);
		registry.add("spring.datasource.master.username", () -> "root");
		registry.add("spring.datasource.master.password", () -> ROOT_PASSWORD);
		registry.add("spring.datasource.tenant.host", MYSQL::getHost);
		registry.add("spring.datasource.tenant.port", () -> MYSQL.getMappedPort(3306));
		registry.add("spring.datasource.tenant.username", () -> "root");
		registry.add("spring.datasource.tenant.password", () -> ROOT_PASSWORD);
		registry.add("spring.datasource.tenant.jdbc-parameters", () -> PARAMS);
		registry.add("sprintmodus.jwt.secret", () -> "integration-test-secret-that-is-long-enough-for-hs512-0123456789-0123456789");
		registry.add("sprintmodus.tenant-credentials.encryption-password", () -> "integration-test-key");
		registry.add("sprintmodus.tenant-credentials.salt", () -> "cafebabecafebabe");
	}

	@Autowired
	MockMvc mvc;

	@Autowired
	JsonMapper json;

	@Autowired
	@Qualifier("masterDataSource")
	DataSource masterDataSource;

	@Autowired
	TenantProvisioner provisioner;

	private JdbcTemplate master() {
		return new JdbcTemplate(masterDataSource);
	}

	/** A connection to one tenant's database, independent of the application's own pools. */
	private JdbcTemplate tenantDb(String databaseName) {
		return new JdbcTemplate(new DriverManagerDataSource(
				"jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + databaseName + "?" + PARAMS,
				"root", ROOT_PASSWORD));
	}

	/** A registered organization: its code, its owner, and the response of the registration call. */
	private record Org(String code, String email) {
	}

	private Org newOrg() {
		String suffix = "t" + SEQUENCE.incrementAndGet() + Long.toString(System.nanoTime() % 100_000, 36);
		return new Org("org-" + suffix, "owner-" + suffix + "@example.com");
	}

	private ResultActions postJson(String path, String body) throws Exception {
		return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions register(String code, String email, String password) throws Exception {
		return postJson("/auth/register-organization", """
				{"organizationName":"Org %s","organizationCode":"%s","fullName":"Owner of %s","email":"%s","password":"%s"}
				""".formatted(code, code, code, email, password));
	}

	private Org registerOrg() throws Exception {
		Org org = newOrg();
		register(org.code(), org.email(), PASSWORD).andExpect(status().isCreated());
		return org;
	}

	private ResultActions login(String code, String email, String password) throws Exception {
		return postJson("/auth/login", """
				{"organizationCode":"%s","email":"%s","password":"%s"}
				""".formatted(code, email, password));
	}

	private Map<?, ?> loginOk(Org org) throws Exception {
		String body = login(org.code(), org.email(), PASSWORD).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
		return json.readValue(body, Map.class);
	}

	private String tenantIdOf(String code) {
		return master().queryForObject("SELECT BIN_TO_UUID(TenantId) FROM Organization WHERE OrganizationCode = ?",
				String.class, code);
	}

	private static String databaseNameOf(String tenantId) {
		return "tenant_" + tenantId.replace("-", "");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> claimsOf(String token) {
		String payload = token.split("\\.")[1];
		return json.readValue(new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8), Map.class);
	}

	private boolean databaseExists(String name) {
		return !master().queryForList("SHOW DATABASES LIKE '" + name + "'").isEmpty();
	}

	// ---------------------------------------------------------------- registration

	@Test
	void registrationProvisionsTheTenantDatabaseAndRecordsTheOrganization() throws Exception {
		Org org = newOrg();

		register(org.code().toUpperCase(), org.email(), PASSWORD)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.organizationCode").value(org.code()))
				.andExpect(jsonPath("$.plan").value("FREE"));

		// Master DB: organization, its FREE subscription and the audit trail
		Map<String, Object> row = master().queryForMap("""
				SELECT OrganizationId, DatabaseName, DatabaseHost, DatabaseUsername, DatabasePassword, IsActive,
				       LENGTH(TenantId) AS TenantIdBytes, BIN_TO_UUID(TenantId) AS TenantId
				FROM Organization WHERE OrganizationCode = ?""", org.code());
		String tenantId = (String) row.get("TenantId");
		assertThat(row.get("TenantIdBytes")).as("UUID stored as BINARY(16)").isEqualTo(16L);
		assertThat(UUID.fromString(tenantId).version()).as("random v4 UUID").isEqualTo(4);
		assertThat(row.get("DatabaseName")).isEqualTo(databaseNameOf(tenantId));
		assertThat((String) row.get("DatabasePassword")).isNotBlank().isNotEqualTo(ROOT_PASSWORD).doesNotContain(ROOT_PASSWORD);

		Map<String, Object> subscription = master().queryForMap(
				"SELECT PlanType, MaxProjects, MaxUsers, MaxStorageMB, ExpiresAt FROM OrganizationSubscription WHERE OrganizationId = ?",
				row.get("OrganizationId"));
		assertThat(subscription).containsEntry("PlanType", "FREE").containsEntry("MaxProjects", 1)
				.containsEntry("MaxUsers", 5).containsEntry("MaxStorageMB", 100).containsEntry("ExpiresAt", null);

		List<Map<String, Object>> audit = master().queryForList(
				"SELECT Action, EntityType, BIN_TO_UUID(ChangedBy) AS ChangedBy, NewValue FROM AuditLog WHERE OrganizationId = ? ORDER BY AuditId",
				row.get("OrganizationId"));
		assertThat(audit).extracting(entry -> entry.get("Action")).containsExactly("ORGANIZATION_REGISTERED", "SUBSCRIPTION_CREATED");
		assertThat(audit.getFirst().get("NewValue").toString()).contains(org.code());

		// Tenant DB: created, migrated, and holding exactly the owner
		String database = databaseNameOf(tenantId);
		assertThat(databaseExists(database)).isTrue();
		JdbcTemplate tenant = tenantDb(database);
		assertThat(tenant.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class))
				.isPositive();
		Map<String, Object> owner = tenant.queryForMap(
				"SELECT Email, Role, PasswordHash, LENGTH(UserCode) AS CodeBytes, BIN_TO_UUID(UserCode) AS UserCode FROM `User`");
		assertThat(owner).containsEntry("Email", org.email()).containsEntry("Role", "OWNER").containsEntry("CodeBytes", 16L);
		assertThat((String) owner.get("PasswordHash")).startsWith("$2").doesNotContain(PASSWORD);
		assertThat(audit.getFirst().get("ChangedBy")).isEqualTo(owner.get("UserCode"));
	}

	@Test
	void registrationRejectsBadInputWithoutCreatingAnything() throws Exception {
		int databasesBefore = master().queryForList("SHOW DATABASES LIKE 'tenant\\_%'").size();
		Org org = newOrg();

		register("admin", org.email(), PASSWORD).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ORGANIZATION_CODE_RESERVED"));
		register("x", org.email(), PASSWORD).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_ORGANIZATION_CODE"));
		register(org.code(), "not-an-email", PASSWORD).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REGISTRATION_DATA"));
		register(org.code(), org.email(), "short").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REGISTRATION_DATA"));
		postJson("/auth/register-organization", "{not json").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
		mvc.perform(post("/auth/register-organization")).andExpect(status().isBadRequest());

		assertThat(master().queryForList("SHOW DATABASES LIKE 'tenant\\_%'")).hasSize(databasesBefore);
		assertThat(master().queryForObject("SELECT COUNT(*) FROM Organization WHERE OrganizationCode = ?", Integer.class,
				org.code())).isZero();
	}

	@Test
	void aTakenCodeOrEmailIsAConflictRegardlessOfCase() throws Exception {
		Org org = registerOrg();
		Org other = newOrg();

		register(org.code().toUpperCase(), other.email(), PASSWORD).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ORGANIZATION_CODE_TAKEN"));
		register(other.code(), org.email().toUpperCase(), PASSWORD).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
	}

	@Test
	void concurrentRegistrationsOfTheSameCodeYieldOneWinnerAndNoOrphanDatabase() throws Exception {
		Org first = newOrg();
		Org second = newOrg();
		int databasesBefore = master().queryForList("SHOW DATABASES LIKE 'tenant\\_%'").size();
		CountDownLatch start = new CountDownLatch(1);

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			List<Future<Integer>> results = new ArrayList<>();
			for (Org org : List.of(first, second)) {
				results.add(executor.submit(() -> {
					start.await();
					return register(first.code(), org.email(), PASSWORD).andReturn().getResponse().getStatus();
				}));
			}
			start.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> result : results) {
				statuses.add(result.get());
			}
			assertThat(statuses).containsExactlyInAnyOrder(201, 409);
		}

		assertThat(master().queryForList("SHOW DATABASES LIKE 'tenant\\_%'")).hasSize(databasesBefore + 1);
		assertThat(master().queryForObject("SELECT COUNT(*) FROM Organization WHERE OrganizationCode = ?", Integer.class,
				first.code())).isEqualTo(1);
	}

	@Test
	void codeAvailabilityUsesTheSameRulesAsRegistration() throws Exception {
		Org org = registerOrg();

		mvc.perform(get("/auth/organization-code-available").param("code", "brand-new-code"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true))
				.andExpect(jsonPath("$.reason").doesNotExist());
		mvc.perform(get("/auth/organization-code-available").param("code", org.code().toUpperCase()))
				.andExpect(jsonPath("$.available").value(false)).andExpect(jsonPath("$.reason").value("ORGANIZATION_CODE_TAKEN"));
		mvc.perform(get("/auth/organization-code-available").param("code", "www"))
				.andExpect(jsonPath("$.available").value(false)).andExpect(jsonPath("$.reason").value("ORGANIZATION_CODE_RESERVED"));
		mvc.perform(get("/auth/organization-code-available").param("code", "-x-"))
				.andExpect(jsonPath("$.available").value(false)).andExpect(jsonPath("$.reason").value("INVALID_ORGANIZATION_CODE"));
		mvc.perform(get("/auth/organization-code-available")).andExpect(status().isBadRequest());
	}

	// ---------------------------------------------------------------- login

	@Test
	void loginReturnsATokenWithTheTenantAndSubscriptionClaims() throws Exception {
		Org org = registerOrg();
		String tenantId = tenantIdOf(org.code());

		Map<?, ?> session = loginOk(org);

		String token = (String) session.get("token");
		Map<String, Object> claims = claimsOf(token);
		String userCode = tenantDb(databaseNameOf(tenantId)).queryForObject("SELECT BIN_TO_UUID(UserCode) FROM `User`", String.class);
		assertThat(claims).containsEntry("sub", userCode).containsEntry("email", org.email())
				.containsEntry("tenantId", tenantId).containsEntry("organizationCode", org.code())
				.containsEntry("role", "OWNER").containsEntry("plan", "FREE").containsEntry("iss", "sprintmodus-auth")
				.containsKeys("iat", "exp", "maxProjects", "maxUsers", "maxStorageMB");
		assertThat(claims).doesNotContainKeys("dbHost", "dbName");
		assertThat(((Number) claims.get("maxProjects")).intValue()).isEqualTo(1);
		assertThat(((Number) claims.get("maxUsers")).intValue()).isEqualTo(5);
		assertThat(((Number) claims.get("maxStorageMB")).intValue()).isEqualTo(100);

		assertThat(session.get("user")).isEqualTo(Map.of("id", userCode, "email", org.email(), "fullName", "Owner of " + org.code(), "role", "OWNER"));
		assertThat(session.get("organization")).isEqualTo(Map.of("id", tenantId, "code", org.code(), "name", "Org " + org.code()));
		assertThat(session.get("subscription")).isEqualTo(Map.of("plan", "FREE", "maxProjects", 1, "maxUsers", 5, "maxStorageMB", 100));
	}

	@Test
	void loginAcceptsTheCodeAndEmailInAnyCaseAndRecordsTheLoginTime() throws Exception {
		Org org = registerOrg();
		String database = databaseNameOf(tenantIdOf(org.code()));
		assertThat(tenantDb(database).queryForObject("SELECT LastLoginAt FROM `User`", Timestamp.class)).isNull();

		login(org.code().toUpperCase(), org.email().toUpperCase(), PASSWORD).andExpect(status().isOk());

		long storedEpoch = tenantDb(database).queryForObject("SELECT UNIX_TIMESTAMP(LastLoginAt) FROM `User`", Long.class);
		assertThat(Instant.ofEpochSecond(storedEpoch)).isCloseTo(Instant.now(), within(30, ChronoUnit.SECONDS));
	}

	@Test
	void everyKindOfBadCredentialGetsTheSame401() throws Exception {
		Org org = registerOrg();

		List<String> bodies = new ArrayList<>();
		for (ResultActions attempt : List.of(login("no-such-org", org.email(), PASSWORD),
				login(org.code(), "nobody@example.com", PASSWORD), login(org.code(), org.email(), "wrong-password"),
				login(org.code(), org.email(), ""))) {
			bodies.add(attempt.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
					.andReturn().getResponse().getContentAsString().replaceAll("\"timestamp\":\"[^\"]*\"", ""));
		}
		assertThat(bodies).containsOnly(bodies.getFirst());
		postJson("/auth/login", "{}").andExpect(status().isUnauthorized());
	}

	@Test
	void aTenantsUserCannotLogIntoAnotherTenant() throws Exception {
		Org a = registerOrg();
		Org b = registerOrg();

		login(a.code(), b.email(), PASSWORD).andExpect(status().isUnauthorized());
		login(b.code(), a.email(), PASSWORD).andExpect(status().isUnauthorized());

		Map<String, Object> claimsA = claimsOf((String) loginOk(a).get("token"));
		Map<String, Object> claimsB = claimsOf((String) loginOk(b).get("token"));
		assertThat(claimsA.get("tenantId")).isNotEqualTo(claimsB.get("tenantId"));
		assertThat(tenantDb(databaseNameOf(tenantIdOf(a.code()))).queryForList("SELECT Email FROM `User`", String.class))
				.containsExactly(a.email());
		assertThat(tenantDb(databaseNameOf(tenantIdOf(b.code()))).queryForList("SELECT Email FROM `User`", String.class))
				.containsExactly(b.email());
	}

	@Test
	void anExpiredOrInactiveSubscriptionIsA402AndAnInactiveOrganizationIsA401() throws Exception {
		Org org = registerOrg();
		String subscription = "UPDATE OrganizationSubscription SET %s WHERE OrganizationId = (SELECT OrganizationId FROM Organization WHERE OrganizationCode = ?)";

		master().update(subscription.formatted("ExpiresAt = ?"), Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)), org.code());
		login(org.code(), org.email(), PASSWORD).andExpect(status().isPaymentRequired())
				.andExpect(jsonPath("$.code").value("SUBSCRIPTION_INACTIVE"));

		master().update(subscription.formatted("ExpiresAt = ?"), Timestamp.from(Instant.now().plus(1, ChronoUnit.DAYS)), org.code());
		login(org.code(), org.email(), PASSWORD).andExpect(status().isOk());

		master().update(subscription.formatted("IsActive = FALSE"), org.code());
		login(org.code(), org.email(), PASSWORD).andExpect(status().isPaymentRequired());
		master().update(subscription.formatted("IsActive = TRUE"), org.code());

		master().update("UPDATE Organization SET IsActive = FALSE WHERE OrganizationCode = ?", org.code());
		login(org.code(), org.email(), PASSWORD).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void aDeactivatedOrSoftDeletedUserCannotLogIn() throws Exception {
		Org org = registerOrg();
		JdbcTemplate tenant = tenantDb(databaseNameOf(tenantIdOf(org.code())));

		tenant.update("UPDATE `User` SET IsActive = FALSE");
		login(org.code(), org.email(), PASSWORD).andExpect(status().isUnauthorized());

		tenant.update("UPDATE `User` SET IsActive = TRUE, DeletedAt = NOW()");
		login(org.code(), org.email(), PASSWORD).andExpect(status().isUnauthorized());

		tenant.update("UPDATE `User` SET DeletedAt = NULL");
		login(org.code(), org.email(), PASSWORD).andExpect(status().isOk());
	}

	// ---------------------------------------------------------------- tokens

	@Test
	void validateTokenAcceptsATokenWeIssuedAndRejectsOthers() throws Exception {
		Org org = registerOrg();
		String token = (String) loginOk(org).get("token");

		mvc.perform(get("/auth/validate-token").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true))
				.andExpect(jsonPath("$.tenantId").value(tenantIdOf(org.code())))
				.andExpect(jsonPath("$.organizationCode").value(org.code())).andExpect(jsonPath("$.role").value("OWNER"));

		mvc.perform(get("/auth/validate-token").header("Authorization", "Bearer " + token + "x"))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
		mvc.perform(get("/auth/validate-token").header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());
		mvc.perform(get("/auth/validate-token").header("Authorization", token)).andExpect(status().isUnauthorized());
		mvc.perform(get("/auth/validate-token")).andExpect(status().isUnauthorized());
	}

	@Test
	void refreshIssuesAFreshSessionAndPicksUpPlanChanges() throws Exception {
		Org org = registerOrg();
		String token = (String) loginOk(org).get("token");
		master().update("""
				UPDATE OrganizationSubscription SET PlanType = 'PRO', MaxProjects = 10, MaxUsers = 50, MaxStorageMB = 5000
				WHERE OrganizationId = (SELECT OrganizationId FROM Organization WHERE OrganizationCode = ?)""", org.code());

		String body = mvc.perform(post("/auth/refresh-token").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.subscription.plan").value("PRO"))
				.andExpect(jsonPath("$.subscription.maxProjects").value(10)).andReturn().getResponse().getContentAsString();

		Map<String, Object> claims = claimsOf((String) json.readValue(body, Map.class).get("token"));
		assertThat(claims).containsEntry("plan", "PRO");
		assertThat(((Number) claims.get("maxUsers")).intValue()).isEqualTo(50);
	}

	@Test
	void refreshIsRefusedForBadTokensAndForOrganizationsThatNoLongerQualify() throws Exception {
		Org org = registerOrg();
		String token = (String) loginOk(org).get("token");

		mvc.perform(post("/auth/refresh-token")).andExpect(status().isUnauthorized());
		mvc.perform(post("/auth/refresh-token").header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());

		master().update("UPDATE OrganizationSubscription SET ExpiresAt = ? WHERE OrganizationId = (SELECT OrganizationId FROM Organization WHERE OrganizationCode = ?)",
				Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)), org.code());
		mvc.perform(post("/auth/refresh-token").header("Authorization", "Bearer " + token)).andExpect(status().isPaymentRequired());
		master().update("UPDATE OrganizationSubscription SET ExpiresAt = NULL WHERE OrganizationId = (SELECT OrganizationId FROM Organization WHERE OrganizationCode = ?)", org.code());

		tenantDb(databaseNameOf(tenantIdOf(org.code()))).update("UPDATE `User` SET IsActive = FALSE");
		mvc.perform(post("/auth/refresh-token").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
	}

	// ---------------------------------------------------------------- surface and provisioning

	@Test
	void everythingOutsideTheAuthEndpointsIsClosedAndWrongMethodsAreRejected() throws Exception {
		mvc.perform(get("/api/projects")).andExpect(status().is4xxClientError());
		mvc.perform(get("/auth/login")).andExpect(status().isMethodNotAllowed());
	}

	@Test
	void theProvisionerCreatesAMigratedDatabaseAndDropsItIdempotently() {
		UUID tenantId = UUID.randomUUID();
		String database = databaseNameOf(tenantId.toString());

		TenantProvisioner.TenantDatabase created = provisioner.create(tenantId);

		assertThat(created.name()).isEqualTo(database);
		assertThat(created.encryptedPassword()).isNotBlank().doesNotContain(ROOT_PASSWORD);
		assertThat(tenantDb(database).queryForObject("SELECT COUNT(*) FROM `User`", Integer.class)).isZero();

		provisioner.drop(tenantId);
		assertThat(databaseExists(database)).isFalse();
		provisioner.drop(tenantId);
	}

}

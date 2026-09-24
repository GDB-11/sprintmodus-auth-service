package com.sprintmodus.auth_service.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sprintmodus.auth_service.application.dto.AuthenticatedSession;
import com.sprintmodus.auth_service.application.dto.LoginCommand;
import com.sprintmodus.auth_service.application.error.AuthenticationError;
import com.sprintmodus.auth_service.application.service.LoginUseCase;
import com.sprintmodus.auth_service.application.service.SessionIssuer;
import com.sprintmodus.auth_service.domain.model.Organization;
import com.sprintmodus.auth_service.domain.model.OrganizationRole;
import com.sprintmodus.auth_service.domain.model.Plan;
import com.sprintmodus.auth_service.domain.model.TenantUser;
import com.sprintmodus.auth_service.support.Fakes;
import com.sprintmodus.common_lib.result.Result;

class LoginUseCaseTest {

	private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");

	private final Fakes.Organizations organizations = new Fakes.Organizations();

	private final Fakes.Subscriptions subscriptions = new Fakes.Subscriptions();

	private final Fakes.TenantUsers users = new Fakes.TenantUsers();

	private final Fakes.Hasher hasher = new Fakes.Hasher();

	private final Fakes.Tokens tokens = new Fakes.Tokens();

	private final UUID tenantId = UUID.randomUUID();

	private LoginUseCase login;

	private Organization acme;

	private TenantUser ana;

	@BeforeEach
	void setUp() {
		login = new LoginUseCase(organizations, subscriptions, users, hasher, new SessionIssuer(tokens), Clock.fixed(NOW, ZoneOffset.UTC));
		acme = organizations.add("acme", tenantId, true);
		subscriptions.add(acme.id(), Plan.PRO, null, true);
		ana = users.add(tenantId, "ana@acme.io", "hashed:s3cret-pass", OrganizationRole.ADMIN, true);
		hasher.comparisons = 0;
	}

	private Result<AuthenticatedSession, AuthenticationError> attempt(String code, String email, String password) {
		return login.execute(new LoginCommand(code, email, password));
	}

	@Test
	void logsInAndBuildsTheSessionFromTheOrganizationSubscriptionAndUser() {
		Result<AuthenticatedSession, AuthenticationError> result = attempt("acme", "ana@acme.io", "s3cret-pass");

		assertThat(result.isSuccess()).isTrue();
		AuthenticatedSession session = result.getValue();
		assertThat(session.token()).isEqualTo("token-for-ana@acme.io");
		assertThat(session.user().id()).isEqualTo(ana.userCode());
		assertThat(session.user().role()).isEqualTo(OrganizationRole.ADMIN);
		assertThat(session.organization().id()).isEqualTo(tenantId);
		assertThat(session.organization().code()).isEqualTo("acme");
		assertThat(session.subscription().plan()).isEqualTo(Plan.PRO);
		assertThat(session.subscription().maxProjects()).isEqualTo(Plan.PRO.maxProjects());
	}

	@Test
	void theTokenCarriesTenantAndSubscriptionClaims() {
		attempt("acme", "ana@acme.io", "s3cret-pass");

		var claims = tokens.lastIssued;
		assertThat(claims.userCode()).isEqualTo(ana.userCode());
		assertThat(claims.tenantId()).isEqualTo(tenantId);
		assertThat(claims.organizationCode()).isEqualTo("acme");
		assertThat(claims.role()).isEqualTo(OrganizationRole.ADMIN);
		assertThat(claims.plan()).isEqualTo(Plan.PRO);
		assertThat(claims.maxUsers()).isEqualTo(Plan.PRO.maxUsers());
		assertThat(claims.maxStorageMB()).isEqualTo(Plan.PRO.maxStorageMB());
	}

	@Test
	void recordsTheLoginTime() {
		attempt("acme", "ana@acme.io", "s3cret-pass");

		assertThat(users.logins).containsEntry(ana.userCode(), NOW);
	}

	@Test
	void normalizesTheOrganizationCodeAndEmail() {
		assertThat(attempt("  ACME ", " Ana@Acme.IO ", "s3cret-pass").isSuccess()).isTrue();
	}

	@Test
	void aWrongPasswordIsRejectedAndNotRecorded() {
		Result<AuthenticatedSession, AuthenticationError> result = attempt("acme", "ana@acme.io", "wrong");

		assertThat(result.getError()).isInstanceOf(AuthenticationError.InvalidCredentials.class);
		assertThat(users.logins).isEmpty();
	}

	@Test
	void unknownOrganizationUnknownUserWrongPasswordAndInactiveUserAllFailIdentically() {
		organizations.add("dormant", UUID.randomUUID(), false);
		users.add(tenantId, "gone@acme.io", "hashed:s3cret-pass", OrganizationRole.MEMBER, false);

		var results = java.util.List.of(
				attempt("nope", "ana@acme.io", "s3cret-pass"),
				attempt("dormant", "ana@acme.io", "s3cret-pass"),
				attempt("acme", "nobody@acme.io", "s3cret-pass"),
				attempt("acme", "ana@acme.io", "wrong"),
				attempt("acme", "gone@acme.io", "s3cret-pass"));

		assertThat(results).allSatisfy(result -> assertThat(result.getError())
				.isEqualTo(new AuthenticationError.InvalidCredentials()));
	}

	@Test
	void everyCredentialFailurePaysForOnePasswordComparison() {
		organizations.add("dormant", UUID.randomUUID(), false);

		for (String[] attempt : new String[][] { { "nope", "ana@acme.io", "x" }, { "dormant", "ana@acme.io", "x" },
				{ "acme", "nobody@acme.io", "x" }, { "acme", "ana@acme.io", "x" } }) {
			hasher.comparisons = 0;
			attempt(attempt[0], attempt[1], attempt[2]);
			assertThat(hasher.comparisons).as(String.join("/", attempt)).isEqualTo(1);
		}
	}

	@Test
	void blankOrMissingInputIsJustInvalidCredentials() {
		assertThat(attempt(null, null, null).getError()).isInstanceOf(AuthenticationError.InvalidCredentials.class);
		assertThat(attempt("acme", "ana@acme.io", null).getError()).isInstanceOf(AuthenticationError.InvalidCredentials.class);
	}

	@Test
	void anInactiveSubscriptionIsRefusedBeforeTheTenantDatabaseIsTouched() {
		subscriptions.add(acme.id(), Plan.PRO, null, false);

		Result<AuthenticatedSession, AuthenticationError> result = attempt("acme", "ana@acme.io", "s3cret-pass");

		assertThat(result.getError()).isInstanceOf(AuthenticationError.SubscriptionInactive.class);
		assertThat(users.lookedUpTenants).isEmpty();
	}

	@Test
	void anExpiredSubscriptionIsRefusedAndOneExpiringLaterIsNot() {
		subscriptions.add(acme.id(), Plan.PRO, NOW.minusSeconds(1), true);
		assertThat(attempt("acme", "ana@acme.io", "s3cret-pass").getError())
				.isInstanceOf(AuthenticationError.SubscriptionInactive.class);

		subscriptions.add(acme.id(), Plan.PRO, NOW.plusSeconds(1), true);
		assertThat(attempt("acme", "ana@acme.io", "s3cret-pass").isSuccess()).isTrue();
	}

	@Test
	void anOrganizationWithoutASubscriptionCannotLogIn() {
		Organization bare = organizations.add("bare", UUID.randomUUID(), true);

		assertThat(attempt(bare.code(), "ana@acme.io", "s3cret-pass").getError())
				.isInstanceOf(AuthenticationError.SubscriptionInactive.class);
	}

	@Test
	void looksUsersUpOnlyInTheTenantOfTheOrganizationTyped() {
		UUID otherTenant = UUID.randomUUID();
		Organization other = organizations.add("other", otherTenant, true);
		subscriptions.add(other.id(), Plan.FREE, null, true);
		users.add(otherTenant, "bob@other.io", "hashed:bob-pass-123", OrganizationRole.OWNER, true);

		// Bob's credentials are valid, but not for acme
		assertThat(attempt("acme", "bob@other.io", "bob-pass-123").isFailure()).isTrue();
		assertThat(users.lookedUpTenants).containsOnly(tenantId);
	}

}

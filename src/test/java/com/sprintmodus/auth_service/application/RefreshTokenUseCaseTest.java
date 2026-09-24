package com.sprintmodus.auth_service.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sprintmodus.auth_service.application.dto.TokenClaims;
import com.sprintmodus.auth_service.application.error.TokenError;
import com.sprintmodus.auth_service.application.service.RefreshTokenUseCase;
import com.sprintmodus.auth_service.application.service.SessionIssuer;
import com.sprintmodus.auth_service.domain.model.Organization;
import com.sprintmodus.auth_service.domain.model.OrganizationRole;
import com.sprintmodus.auth_service.domain.model.Plan;
import com.sprintmodus.auth_service.domain.model.TenantUser;
import com.sprintmodus.auth_service.support.Fakes;
import com.sprintmodus.common_lib.result.Result;

class RefreshTokenUseCaseTest {

	private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");

	private final Fakes.Organizations organizations = new Fakes.Organizations();

	private final Fakes.Subscriptions subscriptions = new Fakes.Subscriptions();

	private final Fakes.TenantUsers users = new Fakes.TenantUsers();

	private final Fakes.Tokens tokens = new Fakes.Tokens();

	private final UUID tenantId = UUID.randomUUID();

	private RefreshTokenUseCase refresh;

	private Organization acme;

	private TenantUser ana;

	@BeforeEach
	void setUp() {
		refresh = new RefreshTokenUseCase(tokens, organizations, subscriptions, users, new SessionIssuer(tokens),
				Clock.fixed(NOW, ZoneOffset.UTC));
		acme = organizations.add("acme", tenantId, true);
		subscriptions.add(acme.id(), Plan.FREE, null, true);
		ana = users.add(tenantId, "ana@acme.io", "hashed:x", OrganizationRole.OWNER, true);
		tokens.register("valid", Result.success(new TokenClaims.Verified(claimsFor(tenantId, "acme", ana.userCode()),
				NOW.plusSeconds(60))));
	}

	private TokenClaims claimsFor(UUID tenant, String organizationCode, UUID userCode) {
		return new TokenClaims(userCode, "ana@acme.io", tenant, organizationCode, OrganizationRole.OWNER, Plan.FREE, 1, 5, 100);
	}

	@Test
	void issuesAFreshTokenForAValidOne() {
		var result = refresh.execute("valid");

		assertThat(result.isSuccess()).isTrue();
		assertThat(result.getValue().token()).isEqualTo("token-for-ana@acme.io");
		assertThat(result.getValue().user().id()).isEqualTo(ana.userCode());
	}

	@Test
	void picksUpAPlanChangeMadeSinceTheOldTokenWasIssued() {
		subscriptions.add(acme.id(), Plan.ENTERPRISE, null, true);

		var result = refresh.execute("valid");

		assertThat(result.getValue().subscription().plan()).isEqualTo(Plan.ENTERPRISE);
		assertThat(tokens.lastIssued.maxProjects()).isEqualTo(Plan.ENTERPRISE.maxProjects());
	}

	@Test
	void rejectsAnUnknownOrMissingToken() {
		assertThat(refresh.execute("garbage").getError()).isInstanceOf(TokenError.InvalidToken.class);
		assertThat(refresh.execute(null).getError()).isInstanceOf(TokenError.InvalidToken.class);
	}

	@Test
	void doesNotRefreshAnExpiredToken() {
		tokens.register("old", Result.failure(new TokenError.ExpiredToken()));

		assertThat(refresh.execute("old").getError()).isInstanceOf(TokenError.ExpiredToken.class);
	}

	@Test
	void rejectsATokenWhoseTenantDoesNotBelongToItsOrganization() {
		tokens.register("mismatch", Result.success(new TokenClaims.Verified(
				claimsFor(UUID.randomUUID(), "acme", ana.userCode()), NOW.plusSeconds(60))));

		assertThat(refresh.execute("mismatch").getError()).isInstanceOf(TokenError.InvalidToken.class);
	}

	@Test
	void rejectsATokenForADeactivatedOrganization() {
		organizations.stored.clear();
		organizations.add("acme", tenantId, false);

		assertThat(refresh.execute("valid").getError()).isInstanceOf(TokenError.InvalidToken.class);
	}

	@Test
	void reportsAnInactiveSubscriptionSeparately() {
		subscriptions.add(acme.id(), Plan.FREE, NOW.minusSeconds(1), true);

		assertThat(refresh.execute("valid").getError()).isInstanceOf(TokenError.SubscriptionInactive.class);
	}

	@Test
	void rejectsATokenOfAUserWhoWasDeactivatedOrDeleted() {
		TenantUser gone = users.add(tenantId, "gone@acme.io", "hashed:x", OrganizationRole.MEMBER, false);
		tokens.register("gone", Result.success(new TokenClaims.Verified(claimsFor(tenantId, "acme", gone.userCode()),
				NOW.plusSeconds(60))));
		tokens.register("missing", Result.success(new TokenClaims.Verified(
				claimsFor(tenantId, "acme", UUID.randomUUID()), NOW.plusSeconds(60))));

		assertThat(refresh.execute("gone").getError()).isInstanceOf(TokenError.InvalidToken.class);
		assertThat(refresh.execute("missing").getError()).isInstanceOf(TokenError.InvalidToken.class);
	}

}

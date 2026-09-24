package com.sprintmodus.auth_service.adapter.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sprintmodus.auth_service.application.dto.TokenClaims;
import com.sprintmodus.auth_service.domain.model.OrganizationRole;
import com.sprintmodus.auth_service.domain.model.Plan;
import com.sprintmodus.auth_service.infrastructure.config.JwtProperties;
import com.sprintmodus.common_lib.security.AuthenticatedUser;
import com.sprintmodus.common_lib.security.JwtTokenVerifier;
import com.sprintmodus.common_lib.security.JwtVerificationProperties;
import com.sprintmodus.common_lib.security.TokenProblem;

/**
 * The token contract between services: what auth-service issues is exactly what common-lib's verifier (used by
 * project-service and workitem-service) accepts, claim for claim. They live in different repositories, so this is where
 * a drift in claim names or types shows up.
 */
class TokenContractTest {

	private static final String SECRET = "contract-test-secret-that-is-long-enough-for-hs512-0123456789-0123456789-abc";

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-23T12:00:00Z"), ZoneOffset.UTC);

	private final JjwtTokenProvider issuer = new JjwtTokenProvider(new JwtProperties(SECRET, "sprintmodus-auth", Duration.ofHours(1)), CLOCK);

	private final JwtTokenVerifier verifier = new JwtTokenVerifier(new JwtVerificationProperties(SECRET, "sprintmodus-auth"), CLOCK);

	@Test
	void everyClaimAuthServiceIssuesIsReadBackByTheSharedVerifier() {
		TokenClaims claims = new TokenClaims(UUID.randomUUID(), "ana@acme.io", UUID.randomUUID(), "acme", OrganizationRole.OWNER,
				Plan.ENTERPRISE, 999, 998, 100_000);

		var result = verifier.verify(issuer.generate(claims));

		assertThat(result.getValue()).isEqualTo(new AuthenticatedUser(claims.userCode(), "ana@acme.io", claims.tenantId(), "acme",
				"OWNER", "ENTERPRISE", 999, 998, 100_000));
	}

	@Test
	void everyRoleAndPlanIsAccepted() {
		for (OrganizationRole role : OrganizationRole.values()) {
			for (Plan plan : Plan.values()) {
				var token = issuer.generate(new TokenClaims(UUID.randomUUID(), "a@b.io", UUID.randomUUID(), "acme", role, plan,
						plan.maxProjects(), plan.maxUsers(), plan.maxStorageMB()));

				assertThat(verifier.verify(token).getValue().role()).isEqualTo(role.name());
			}
		}
	}

	@Test
	void theSharedVerifierAgreesOnExpiry() {
		TokenClaims claims = new TokenClaims(UUID.randomUUID(), "a@b.io", UUID.randomUUID(), "acme", OrganizationRole.MEMBER, Plan.FREE,
				1, 5, 100);
		String token = issuer.generate(claims);
		JwtTokenVerifier later = new JwtTokenVerifier(new JwtVerificationProperties(SECRET, "sprintmodus-auth"),
				Clock.fixed(Instant.parse("2026-09-23T13:00:01Z"), ZoneOffset.UTC));

		assertThat(later.verify(token).getError()).isEqualTo(TokenProblem.EXPIRED);
	}

}

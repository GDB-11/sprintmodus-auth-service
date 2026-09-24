package com.sprintmodus.auth_service.adapter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import com.sprintmodus.auth_service.application.dto.TokenClaims;
import com.sprintmodus.auth_service.application.error.TokenError;
import com.sprintmodus.auth_service.domain.model.OrganizationRole;
import com.sprintmodus.auth_service.domain.model.Plan;
import com.sprintmodus.auth_service.infrastructure.config.JwtProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JjwtTokenProviderTest {

	private static final String SECRET = "unit-test-secret-that-is-long-enough-for-hs512-0123456789-0123456789-abcd";

	private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");

	private static final Duration LIFETIME = Duration.ofHours(1);

	/** A clock the test can move forward. */
	private static final class TestClock extends Clock {

		Instant now = NOW;

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

	private final TestClock clock = new TestClock();

	private final JjwtTokenProvider provider = provider(SECRET, "sprintmodus-auth");

	private final TokenClaims claims = new TokenClaims(UUID.randomUUID(), "ana@acme.io", UUID.randomUUID(), "acme",
			OrganizationRole.ADMIN, Plan.PRO, 10, 50, 5000);

	private JjwtTokenProvider provider(String secret, String issuer) {
		return new JjwtTokenProvider(new JwtProperties(secret, issuer, LIFETIME), clock);
	}

	private Claims payloadOf(String token) {
		SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
		return Jwts.parser().verifyWith(key).clock(() -> Date.from(NOW)).build().parseSignedClaims(token).getPayload();
	}

	@Test
	void roundTripsEveryClaim() {
		String token = provider.generate(claims);

		var verified = provider.verify(token).getValue();

		assertThat(verified.claims()).isEqualTo(claims);
		assertThat(verified.expiresAt()).isEqualTo(NOW.plus(LIFETIME));
	}

	@Test
	void containsTheDocumentedClaimsAndNoDatabaseDetails() {
		Claims payload = payloadOf(provider.generate(claims));

		assertThat(payload.keySet()).containsExactlyInAnyOrder("sub", "email", "tenantId", "organizationCode", "role",
				"plan", "maxProjects", "maxUsers", "maxStorageMB", "iat", "exp", "iss");
		assertThat(payload.keySet()).doesNotContain("dbHost", "dbName");
		assertThat(payload.getSubject()).isEqualTo(claims.userCode().toString());
		assertThat(payload.getIssuer()).isEqualTo("sprintmodus-auth");
		assertThat(payload.get("tenantId")).isEqualTo(claims.tenantId().toString());
		assertThat(payload.get("role")).isEqualTo("ADMIN");
		assertThat(payload.getIssuedAt().toInstant()).isEqualTo(NOW);
	}

	@Test
	void signsWithHs512() {
		String header = new String(Base64.getUrlDecoder().decode(provider.generate(claims).split("\\.")[0]), StandardCharsets.UTF_8);

		assertThat(header).contains("HS512");
	}

	@Test
	void rejectsAnExpiredTokenAsExpired() {
		String token = provider.generate(claims);

		clock.now = NOW.plus(LIFETIME).plusSeconds(1);

		assertThat(provider.verify(token).getError()).isInstanceOf(TokenError.ExpiredToken.class);
	}

	@Test
	void acceptsATokenUntilItExpires() {
		String token = provider.generate(claims);

		clock.now = NOW.plus(LIFETIME).minusSeconds(1);

		assertThat(provider.verify(token).isSuccess()).isTrue();
	}

	@Test
	void rejectsATamperedPayload() {
		String[] parts = provider.generate(claims).split("\\.");
		String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
				"{\"sub\":\"x\",\"role\":\"OWNER\"}".getBytes(StandardCharsets.UTF_8));

		String forged = parts[0] + "." + forgedPayload + "." + parts[2];

		assertThat(provider.verify(forged).getError()).isInstanceOf(TokenError.InvalidToken.class);
	}

	@Test
	void rejectsATokenSignedWithAnotherKey() {
		String foreign = provider("another-secret-that-is-long-enough-for-hs512-0123456789-0123456789-abcdef", "sprintmodus-auth")
				.generate(claims);

		assertThat(provider.verify(foreign).getError()).isInstanceOf(TokenError.InvalidToken.class);
	}

	@Test
	void rejectsATokenFromAnotherIssuer() {
		String other = provider(SECRET, "someone-else").generate(claims);

		assertThat(provider.verify(other).getError()).isInstanceOf(TokenError.InvalidToken.class);
	}

	@Test
	void rejectsAnUnsignedToken() {
		String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
		String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
				("{\"sub\":\"" + claims.userCode() + "\",\"iss\":\"sprintmodus-auth\"}").getBytes(StandardCharsets.UTF_8));

		assertThat(provider.verify(header + "." + payload + ".").getError()).isInstanceOf(TokenError.InvalidToken.class);
	}

	@Test
	void rejectsAValidlySignedTokenWithMissingOrMalformedClaims() {
		SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
		String missing = Jwts.builder().issuer("sprintmodus-auth").subject(claims.userCode().toString())
				.expiration(Date.from(NOW.plus(LIFETIME))).signWith(key, Jwts.SIG.HS512).compact();
		String malformed = Jwts.builder().issuer("sprintmodus-auth").subject("not-a-uuid")
				.expiration(Date.from(NOW.plus(LIFETIME))).claims(Map.of("email", "a@b.io", "tenantId", "x",
						"organizationCode", "acme", "role", "GOD", "plan", "PRO", "maxProjects", 1, "maxUsers", 1,
						"maxStorageMB", 1))
				.signWith(key, Jwts.SIG.HS512).compact();

		assertThat(provider.verify(missing).getError()).isInstanceOf(TokenError.InvalidToken.class);
		assertThat(provider.verify(malformed).getError()).isInstanceOf(TokenError.InvalidToken.class);
	}

	@Test
	void rejectsGarbageBlankAndNull() {
		assertThat(provider.verify("garbage").getError()).isInstanceOf(TokenError.InvalidToken.class);
		assertThat(provider.verify("a.b.c").getError()).isInstanceOf(TokenError.InvalidToken.class);
		assertThat(provider.verify("  ").getError()).isInstanceOf(TokenError.InvalidToken.class);
		assertThat(provider.verify(null).getError()).isInstanceOf(TokenError.InvalidToken.class);
	}

	@Test
	void refusesToStartWithAWeakOrMissingSecret() {
		assertThatThrownBy(() -> new JwtProperties("too-short", "iss", LIFETIME)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("JWT_SECRET");
		assertThatThrownBy(() -> new JwtProperties("", "iss", LIFETIME)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new JwtProperties(null, "iss", LIFETIME)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new JwtProperties(SECRET, "iss", Duration.ZERO)).isInstanceOf(IllegalStateException.class);
	}

}

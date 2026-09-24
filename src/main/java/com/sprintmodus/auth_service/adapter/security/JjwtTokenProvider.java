package com.sprintmodus.auth_service.adapter.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.sprintmodus.auth_service.application.dto.TokenClaims;
import com.sprintmodus.auth_service.application.error.TokenError;
import com.sprintmodus.auth_service.application.port.external.TokenProvider;
import com.sprintmodus.auth_service.domain.model.OrganizationRole;
import com.sprintmodus.auth_service.domain.model.Plan;
import com.sprintmodus.auth_service.infrastructure.config.JwtProperties;
import com.sprintmodus.common_lib.result.Result;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Issues and verifies HS512-signed JWTs with the claims {@code sub} (user code), {@code email}, {@code tenantId},
 * {@code organizationCode}, {@code role}, {@code plan}, {@code maxProjects}, {@code maxUsers}, {@code maxStorageMB},
 * {@code iat}, {@code exp} and {@code iss}.
 */
@Component
class JjwtTokenProvider implements TokenProvider {

	private final SecretKey key;

	private final JwtProperties properties;

	private final Clock clock;

	JjwtTokenProvider(JwtProperties properties, Clock clock) {
		this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
		this.properties = properties;
		this.clock = clock;
	}

	@Override
	public String generate(TokenClaims claims) {
		Instant issuedAt = clock.instant();
		return Jwts.builder()
				.issuer(properties.issuer())
				.subject(claims.userCode().toString())
				.issuedAt(Date.from(issuedAt))
				.expiration(Date.from(issuedAt.plus(properties.expiration())))
				.claim("email", claims.email())
				.claim("tenantId", claims.tenantId().toString())
				.claim("organizationCode", claims.organizationCode())
				.claim("role", claims.role().name())
				.claim("plan", claims.plan().name())
				.claim("maxProjects", claims.maxProjects())
				.claim("maxUsers", claims.maxUsers())
				.claim("maxStorageMB", claims.maxStorageMB())
				.signWith(key, Jwts.SIG.HS512)
				.compact();
	}

	@Override
	public Result<TokenClaims.Verified, TokenError> verify(String token) {
		if (token == null || token.isBlank()) {
			return Result.failure(new TokenError.InvalidToken());
		}
		try {
			Claims payload = Jwts.parser()
					.verifyWith(key)
					.requireIssuer(properties.issuer())
					.clock(() -> Date.from(clock.instant()))
					.build()
					.parseSignedClaims(token)
					.getPayload();
			return Result.success(new TokenClaims.Verified(toClaims(payload), payload.getExpiration().toInstant()));
		}
		catch (ExpiredJwtException e) {
			return Result.failure(new TokenError.ExpiredToken());
		}
		catch (JwtException | IllegalArgumentException e) {
			// Bad signature, wrong issuer, malformed, or a claim that is missing or not of the expected form
			return Result.failure(new TokenError.InvalidToken());
		}
	}

	private static TokenClaims toClaims(Claims claims) {
		return new TokenClaims(UUID.fromString(required(claims.getSubject(), "sub")),
				required(claims.get("email", String.class), "email"),
				UUID.fromString(required(claims.get("tenantId", String.class), "tenantId")),
				required(claims.get("organizationCode", String.class), "organizationCode"),
				OrganizationRole.valueOf(required(claims.get("role", String.class), "role")),
				Plan.valueOf(required(claims.get("plan", String.class), "plan")),
				intClaim(claims, "maxProjects"), intClaim(claims, "maxUsers"), intClaim(claims, "maxStorageMB"));
	}

	/** Gson reads every JSON number as a Double, which jjwt will not convert to Integer, so go through Number. */
	private static int intClaim(Claims claims, String name) {
		return required(claims.get(name, Number.class), name).intValue();
	}

	private static <T> T required(T value, String claim) {
		if (value == null) {
			throw new IllegalArgumentException("Missing claim " + claim);
		}
		return value;
	}

}

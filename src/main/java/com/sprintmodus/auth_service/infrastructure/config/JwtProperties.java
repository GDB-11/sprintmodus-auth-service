package com.sprintmodus.auth_service.infrastructure.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings ({@code sprintmodus.jwt.*}).
 *
 * @param secret HMAC key shared with every service that validates tokens; at least 64 bytes for HS512
 * @param issuer value of the {@code iss} claim, required on validation
 * @param expiration lifetime of a token
 */
@ConfigurationProperties("sprintmodus.jwt")
public record JwtProperties(String secret, String issuer, Duration expiration) {

	private static final int MIN_SECRET_BYTES = 64;

	public JwtProperties {
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("sprintmodus.jwt.secret (env JWT_SECRET) must be set and have at least "
					+ MIN_SECRET_BYTES + " bytes");
		}
		if (issuer == null || issuer.isBlank()) {
			throw new IllegalStateException("sprintmodus.jwt.issuer must be set");
		}
		if (expiration == null || expiration.isZero() || expiration.isNegative()) {
			throw new IllegalStateException("sprintmodus.jwt.expiration must be a positive duration");
		}
	}

}

package com.sprintmodus.auth_service.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Key material for encrypting the tenant database password recorded in the master DB
 * ({@code sprintmodus.tenant-credentials.*}).
 *
 * @param encryptionPassword secret the encryption key is derived from
 * @param salt hex-encoded salt for that derivation
 */
@ConfigurationProperties("sprintmodus.tenant-credentials")
public record TenantCredentialsProperties(String encryptionPassword, String salt) {

	public TenantCredentialsProperties {
		if (encryptionPassword == null || encryptionPassword.isBlank() || salt == null || salt.isBlank()) {
			throw new IllegalStateException(
					"sprintmodus.tenant-credentials.encryption-password and .salt (env TENANT_CREDENTIALS_KEY and "
							+ "TENANT_CREDENTIALS_SALT) must be set");
		}
	}

}

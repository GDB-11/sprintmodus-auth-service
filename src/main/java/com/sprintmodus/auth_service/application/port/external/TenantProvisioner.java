package com.sprintmodus.auth_service.application.port.external;

import java.util.UUID;

/** Creates and removes the physical database of a tenant. */
public interface TenantProvisioner {

	/**
	 * Where a tenant's database lives.
	 *
	 * @param encryptedPassword the connection password, encrypted; never plain text
	 */
	record TenantDatabase(String host, String name, String username, String encryptedPassword) {
	}

	/**
	 * Creates the database and applies the tenant schema to it. Cleans up after itself and throws
	 * {@link TenantProvisioningException} if it cannot finish.
	 */
	TenantDatabase create(UUID tenantId);

	/** Drops the tenant's database if it exists. Used to roll back a registration that did not complete. */
	void drop(UUID tenantId);

	/** Infrastructure failure while provisioning; surfaces as HTTP 500. */
	class TenantProvisioningException extends RuntimeException {

		public TenantProvisioningException(String message, Throwable cause) {
			super(message, cause);
		}

	}

}

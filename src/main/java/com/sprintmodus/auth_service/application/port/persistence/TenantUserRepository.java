package com.sprintmodus.auth_service.application.port.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.sprintmodus.auth_service.domain.model.OrganizationRole;
import com.sprintmodus.auth_service.domain.model.TenantUser;

/**
 * Users of one tenant, in that tenant's own database. Every method names the tenant explicitly, so a caller can never
 * read another tenant's users by forgetting to set up a context.
 */
public interface TenantUserRepository {

	record NewUser(UUID userCode, String email, String fullName, String passwordHash, OrganizationRole role) {
	}

	Optional<TenantUser> findByEmail(UUID tenantId, String email);

	Optional<TenantUser> findByCode(UUID tenantId, UUID userCode);

	void recordLogin(UUID tenantId, UUID userCode, Instant at);

	void create(UUID tenantId, NewUser user);

}

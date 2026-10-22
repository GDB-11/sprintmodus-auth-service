package com.sprintmodus.auth_service.application.port.persistence;

import java.util.Optional;
import java.util.UUID;

import com.sprintmodus.auth_service.application.port.external.TenantProvisioner.TenantDatabase;
import com.sprintmodus.auth_service.domain.model.Organization;
import com.sprintmodus.auth_service.domain.model.OrganizationRole;
import com.sprintmodus.auth_service.domain.model.Plan;
import com.sprintmodus.common_lib.result.Result;

/** Organizations in the master database. */
public interface OrganizationRepository {

	enum Duplicate {

		CODE,
		EMAIL

	}

	/**
	 * Everything recorded when an organization is registered.
	 *
	 * @param ownerUserCode the tenant user who registered it, for the audit trail
	 */
	record Registration(UUID tenantId, String code, String name, String email, TenantDatabase database, Plan plan,
			UUID ownerUserCode) {
	}

	/** Finds an active or inactive organization by its (already normalized) login code. */
	Optional<Organization> findByCode(String code);

	Optional<Organization> findByTenantId(UUID tenantId);

	boolean existsByCode(String code);

	boolean existsByEmail(String email);

	/**
	 * Atomically records the organization, its subscription and the audit entry. Returns which unique key was violated
	 * if another registration won a race, instead of throwing.
	 */
	Result<Organization, Duplicate> register(Registration registration);

	/** Writes the platform audit entry for a user added to the organization. */
	void recordUserAdded(long organizationId, UUID addedBy, UUID userCode, OrganizationRole role);

	/** Writes the platform audit entry for a plan change made by the organization's owner. */
	void recordPlanChanged(long organizationId, UUID changedBy, Plan from, Plan to);

	/** Writes the platform audit entry for a user an OWNER/ADMIN activated or deactivated. */
	void recordUserActiveChanged(long organizationId, UUID changedBy, UUID userCode, boolean active);

	/** Writes the platform audit entry for a password an OWNER/ADMIN set for someone else, or a user changed for themselves. */
	void recordPasswordChanged(long organizationId, UUID changedBy, UUID userCode);

}

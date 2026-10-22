package com.sprintmodus.auth_service.application.port.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.sprintmodus.auth_service.application.dto.Member;
import com.sprintmodus.auth_service.domain.model.OrganizationRole;
import com.sprintmodus.auth_service.domain.model.TenantUser;
import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.common_lib.result.Unit;

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

	/** Replaces the user's password hash. */
	void updatePassword(UUID tenantId, UUID userCode, String passwordHash);

	enum Rejection {

		EMAIL_TAKEN,
		LIMIT_REACHED

	}

	/** A page of members plus the number of active ones in the whole tenant. */
	record Members(List<Member> items, long total, long activeCount) {
	}

	/**
	 * Inserts the user unless the tenant already has {@code maxUsers} active users or the email exists (also among
	 * deactivated users). The count and the insert happen in one transaction that first locks the tenant's owner row, so
	 * two concurrent calls at {@code maxUsers - 1} cannot both succeed.
	 */
	Result<Unit, Rejection> createWithinLimit(UUID tenantId, NewUser user, int maxUsers);

	/**
	 * Activates or deactivates a user (one that exists and is not soft-deleted). Activating one more user than
	 * {@code maxUsers} allows is refused with {@link Rejection#LIMIT_REACHED}; the count and the update share a transaction
	 * that first locks the owner row, like {@link #createWithinLimit}.
	 */
	Result<Unit, Rejection> setActiveWithinLimit(UUID tenantId, UUID userCode, boolean active, int maxUsers);

	/** Members ordered by role (owner first), then name; soft-deleted users are left out. */
	Members list(UUID tenantId, int page, int size);

}

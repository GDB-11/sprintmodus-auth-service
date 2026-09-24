package com.sprintmodus.auth_service.infrastructure.persistence.tenant;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.sprintmodus.auth_service.application.port.persistence.TenantUserRepository;
import com.sprintmodus.auth_service.domain.model.TenantUser;
import com.sprintmodus.common_lib.tenant.TenantContext;

/**
 * The {@link TenantUserRepository} port, implemented over the native queries of {@link TenantUserQueries}. The tenant
 * context is set around each call, <em>before</em> the query opens its transaction: that is when the routing DataSource
 * picks the tenant's database.
 */
@Repository
class JpaTenantUserRepository implements TenantUserRepository {

	private final TenantUserQueries users;

	JpaTenantUserRepository(TenantUserQueries users) {
		this.users = users;
	}

	@Override
	public Optional<TenantUser> findByEmail(UUID tenantId, String email) {
		return TenantContext.callAs(tenantId, () -> users.findByEmail(email).map(JpaTenantUserRepository::toDomain));
	}

	@Override
	public Optional<TenantUser> findByCode(UUID tenantId, UUID userCode) {
		return TenantContext.callAs(tenantId,
				() -> users.findByCode(userCode.toString()).map(JpaTenantUserRepository::toDomain));
	}

	@Override
	public void recordLogin(UUID tenantId, UUID userCode, Instant at) {
		TenantContext.callAs(tenantId, () -> users.updateLastLogin(userCode.toString(), at));
	}

	@Override
	public void create(UUID tenantId, NewUser user) {
		TenantContext.callAs(tenantId, () -> users.insert(user.userCode().toString(), user.email(), user.passwordHash(),
				user.fullName(), user.role().name()));
	}

	private static TenantUser toDomain(TenantUserEntity entity) {
		return new TenantUser(entity.userCode, entity.email, entity.fullName, entity.passwordHash, entity.role, entity.active);
	}

}

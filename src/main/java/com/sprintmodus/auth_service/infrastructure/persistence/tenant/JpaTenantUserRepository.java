package com.sprintmodus.auth_service.infrastructure.persistence.tenant;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sprintmodus.auth_service.application.dto.Member;
import com.sprintmodus.auth_service.application.port.persistence.TenantUserRepository;
import com.sprintmodus.auth_service.domain.model.TenantUser;
import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.common_lib.result.Unit;
import com.sprintmodus.common_lib.tenant.TenantContext;

/**
 * The {@link TenantUserRepository} port, implemented over the native queries of {@link TenantUserQueries}. The tenant
 * context is set around each call, <em>before</em> the query opens its transaction: that is when the routing DataSource
 * picks the tenant's database.
 */
@Repository
class JpaTenantUserRepository implements TenantUserRepository {

	private final TenantUserQueries users;

	private final TransactionTemplate transaction;

	JpaTenantUserRepository(TenantUserQueries users,
			@Qualifier("tenantTransactionManager") PlatformTransactionManager transactionManager) {
		this.users = users;
		this.transaction = new TransactionTemplate(transactionManager);
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

	@Override
	public void updatePassword(UUID tenantId, UUID userCode, String passwordHash) {
		TenantContext.callAs(tenantId, () -> users.updatePassword(userCode.toString(), passwordHash));
	}

	@Override
	public Result<Unit, Rejection> createWithinLimit(UUID tenantId, NewUser user, int maxUsers) {
		// The context is set before the transaction opens: that is when the routing DataSource picks the database
		return TenantContext.callAs(tenantId, () -> {
			try {
				return transaction.execute(_ -> insertWithinLimit(user, maxUsers));
			}
			catch (DataIntegrityViolationException e) {
				if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("uk_user_email")) {
					return Result.failure(Rejection.EMAIL_TAKEN);
				}
				throw e;
			}
		});
	}

	@Override
	public Result<Unit, Rejection> setActiveWithinLimit(UUID tenantId, UUID userCode, boolean active, int maxUsers) {
		return TenantContext.callAs(tenantId, () -> transaction.execute(_ -> {
			users.lockOwner();
			if (active && users.countActive() >= maxUsers && users.findByCode(userCode.toString()).filter(u -> !u.active).isPresent()) {
				return Result.<Unit, Rejection>failure(Rejection.LIMIT_REACHED);
			}
			users.updateActive(userCode.toString(), active);
			return Result.<Unit, Rejection>success(Unit.VALUE);
		}));
	}

	private Result<Unit, Rejection> insertWithinLimit(NewUser user, int maxUsers) {
		users.lockOwner();
		if (users.countByEmail(user.email()) > 0) {
			return Result.failure(Rejection.EMAIL_TAKEN);
		}
		if (users.countActive() >= maxUsers) {
			return Result.failure(Rejection.LIMIT_REACHED);
		}
		users.insert(user.userCode().toString(), user.email(), user.passwordHash(), user.fullName(), user.role().name());
		return Result.success(Unit.VALUE);
	}

	@Override
	public Members list(UUID tenantId, int page, int size) {
		return TenantContext.callAs(tenantId, () -> {
			List<Member> items = users.findPage(size, (long) page * size).stream()
					.map(e -> new Member(e.userCode, e.fullName, e.email, e.role, e.active, e.lastLoginAt)).toList();
			return new Members(items, users.countAll(), users.countActive());
		});
	}

	private static TenantUser toDomain(TenantUserEntity entity) {
		return new TenantUser(entity.userCode, entity.email, entity.fullName, entity.passwordHash, entity.role, entity.active);
	}

}

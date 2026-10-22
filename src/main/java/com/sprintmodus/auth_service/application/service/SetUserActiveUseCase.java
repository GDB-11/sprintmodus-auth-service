package com.sprintmodus.auth_service.application.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sprintmodus.auth_service.application.dto.SetUserActiveCommand;
import com.sprintmodus.auth_service.application.error.MemberError;
import com.sprintmodus.auth_service.application.port.persistence.OrganizationRepository;
import com.sprintmodus.auth_service.application.port.persistence.TenantUserRepository;
import com.sprintmodus.auth_service.domain.model.TenantUser;
import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.common_lib.result.Unit;

/**
 * An OWNER or ADMIN deactivates a user (who can then no longer log in) or brings them back. The same hierarchy as
 * resetting a password applies: an OWNER manages ADMINs and MEMBERs, an ADMIN manages MEMBERs, nobody manages an OWNER
 * or themselves. Reactivating counts against the plan's {@code maxUsers}.
 */
@Service
public class SetUserActiveUseCase {

	private final MemberCallerResolver callers;

	private final TenantUserRepository users;

	private final OrganizationRepository organizations;

	public SetUserActiveUseCase(MemberCallerResolver callers, TenantUserRepository users, OrganizationRepository organizations) {
		this.callers = callers;
		this.users = users;
		this.organizations = organizations;
	}

	public Result<Unit, MemberError> execute(SetUserActiveCommand command) {
		return callers.resolveManager(command.token()).flatMap(caller -> {
			UUID userCode = parse(command.userCode());
			UUID tenantId = caller.organization().tenantId();
			Optional<TenantUser> target = userCode == null ? Optional.empty() : users.findByCode(tenantId, userCode);
			if (target.isEmpty()) {
				return Result.<Unit, MemberError>failure(new MemberError.UserNotFound());
			}
			if (!CreateUserUseCase.mayGrant(caller.user().role(), target.get().role())) {
				return Result.<Unit, MemberError>failure(new MemberError.RoleNotAllowed());
			}
			int maxUsers = caller.subscription().maxUsers();
			var changed = users.setActiveWithinLimit(tenantId, userCode, command.active(), maxUsers);
			if (changed.isFailure()) {
				return Result.<Unit, MemberError>failure(new MemberError.UserLimitReached(maxUsers));
			}
			if (target.get().active() != command.active()) {
				organizations.recordUserActiveChanged(caller.organization().id(), caller.user().userCode(), userCode, command.active());
			}
			return Result.<Unit, MemberError>success(Unit.VALUE);
		});
	}

	private static UUID parse(String raw) {
		try {
			return raw == null ? null : UUID.fromString(raw);
		}
		catch (IllegalArgumentException _) {
			return null;
		}
	}

}

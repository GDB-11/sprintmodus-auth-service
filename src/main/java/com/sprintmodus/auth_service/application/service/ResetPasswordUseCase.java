package com.sprintmodus.auth_service.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sprintmodus.auth_service.application.dto.ResetPasswordCommand;
import com.sprintmodus.auth_service.application.error.MemberError;
import com.sprintmodus.auth_service.application.port.external.PasswordHasher;
import com.sprintmodus.auth_service.application.port.persistence.OrganizationRepository;
import com.sprintmodus.auth_service.application.port.persistence.TenantUserRepository;
import com.sprintmodus.auth_service.domain.service.PasswordPolicy;
import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.common_lib.result.Unit;

/**
 * An OWNER or ADMIN sets a new password for another user (there are no emails to send a reset link to). The same
 * hierarchy as granting a role applies: an OWNER resets ADMINs and MEMBERs, an ADMIN resets MEMBERs, nobody resets an
 * OWNER or themselves (that is {@link ChangePasswordUseCase}).
 */
@Service
public class ResetPasswordUseCase {

	private final MemberCallerResolver callers;

	private final TenantUserRepository users;

	private final OrganizationRepository organizations;

	private final PasswordHasher hasher;

	public ResetPasswordUseCase(MemberCallerResolver callers, TenantUserRepository users,
			OrganizationRepository organizations, PasswordHasher hasher) {
		this.callers = callers;
		this.users = users;
		this.organizations = organizations;
		this.hasher = hasher;
	}

	public Result<Unit, MemberError> execute(ResetPasswordCommand command) {
		return callers.resolveManager(command.token()).flatMap(caller -> {
			UUID userCode = parse(command.userCode());
			var target = userCode == null ? java.util.Optional.<com.sprintmodus.auth_service.domain.model.TenantUser>empty()
					: users.findByCode(caller.organization().tenantId(), userCode);
			if (target.isEmpty()) {
				return Result.<Unit, MemberError>failure(new MemberError.UserNotFound());
			}
			if (!CreateUserUseCase.mayGrant(caller.user().role(), target.get().role())) {
				return Result.<Unit, MemberError>failure(new MemberError.RoleNotAllowed());
			}
			var password = PasswordPolicy.validate(command.newPassword());
			if (password.isFailure()) {
				return Result.<Unit, MemberError>failure(new MemberError.InvalidUserData("password", password.getError()));
			}
			users.updatePassword(caller.organization().tenantId(), userCode, hasher.hash(command.newPassword()));
			organizations.recordPasswordChanged(caller.organization().id(), caller.user().userCode(), userCode);
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

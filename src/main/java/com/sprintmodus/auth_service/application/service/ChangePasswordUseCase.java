package com.sprintmodus.auth_service.application.service;

import org.springframework.stereotype.Service;

import com.sprintmodus.auth_service.application.dto.ChangePasswordCommand;
import com.sprintmodus.auth_service.application.error.MemberError;
import com.sprintmodus.auth_service.application.port.external.PasswordHasher;
import com.sprintmodus.auth_service.application.port.persistence.OrganizationRepository;
import com.sprintmodus.auth_service.application.port.persistence.TenantUserRepository;
import com.sprintmodus.auth_service.domain.service.PasswordPolicy;
import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.common_lib.result.Unit;

/**
 * A signed-in user changes their own password. The current one must be given, so a stolen or left-open session cannot
 * take the account over. A wrong current password is a 400, not a 401: the frontend signs the user out on any 401.
 */
@Service
public class ChangePasswordUseCase {

	private final MemberCallerResolver callers;

	private final TenantUserRepository users;

	private final OrganizationRepository organizations;

	private final PasswordHasher hasher;

	public ChangePasswordUseCase(MemberCallerResolver callers, TenantUserRepository users,
			OrganizationRepository organizations, PasswordHasher hasher) {
		this.callers = callers;
		this.users = users;
		this.organizations = organizations;
		this.hasher = hasher;
	}

	public Result<Unit, MemberError> execute(ChangePasswordCommand command) {
		return callers.resolveMember(command.token()).flatMap(caller -> {
			String current = command.currentPassword() == null ? "" : command.currentPassword();
			if (!hasher.matches(current, caller.user().passwordHash())) {
				return Result.<Unit, MemberError>failure(new MemberError.WrongCurrentPassword());
			}
			var password = PasswordPolicy.validate(command.newPassword());
			if (password.isFailure()) {
				return Result.<Unit, MemberError>failure(new MemberError.InvalidUserData("password", password.getError()));
			}
			users.updatePassword(caller.organization().tenantId(), caller.user().userCode(),
					hasher.hash(command.newPassword()));
			organizations.recordPasswordChanged(caller.organization().id(), caller.user().userCode(),
					caller.user().userCode());
			return Result.<Unit, MemberError>success(Unit.VALUE);
		});
	}

}

package com.sprintmodus.auth_service.application.service;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sprintmodus.auth_service.application.dto.CreateUserCommand;
import com.sprintmodus.auth_service.application.dto.Member;
import com.sprintmodus.auth_service.application.error.MemberError;
import com.sprintmodus.auth_service.application.port.external.PasswordHasher;
import com.sprintmodus.auth_service.application.port.persistence.OrganizationRepository;
import com.sprintmodus.auth_service.application.port.persistence.TenantUserRepository;
import com.sprintmodus.auth_service.application.port.persistence.TenantUserRepository.NewUser;
import com.sprintmodus.auth_service.domain.model.OrganizationRole;
import com.sprintmodus.auth_service.domain.service.EmailAddress;
import com.sprintmodus.auth_service.domain.service.PasswordPolicy;
import com.sprintmodus.common_lib.result.Result;

/**
 * Adds a user to the caller's organization. An OWNER adds ADMINs or MEMBERs, an ADMIN adds MEMBERs, nobody adds an
 * OWNER. The limit is the subscription's {@code maxUsers} from the master DB, counted over active users, and the count
 * and the insert are atomic in the repository.
 */
@Service
public class CreateUserUseCase {

	private static final int MAX_NAME_LENGTH = 255;

	private final MemberCallerResolver callers;

	private final TenantUserRepository users;

	private final OrganizationRepository organizations;

	private final PasswordHasher hasher;

	public CreateUserUseCase(MemberCallerResolver callers, TenantUserRepository users, OrganizationRepository organizations,
			PasswordHasher hasher) {
		this.callers = callers;
		this.users = users;
		this.organizations = organizations;
		this.hasher = hasher;
	}

	public Result<Member, MemberError> execute(CreateUserCommand command) {
		return callers.resolveManager(command.token()).flatMap(caller -> create(caller, command));
	}

	private Result<Member, MemberError> create(MemberCallerResolver.Caller caller, CreateUserCommand command) {
		OrganizationRole role = parseRole(command.role());
		if (role == null) {
			return invalid("role", "Choose a valid role.");
		}
		if (!mayGrant(caller.user().role(), role)) {
			return Result.failure(new MemberError.RoleNotAllowed());
		}
		String fullName = command.fullName() == null ? "" : command.fullName().trim();
		if (fullName.isEmpty() || fullName.length() > MAX_NAME_LENGTH) {
			return invalid("fullName", "Enter the full name.");
		}
		String email = EmailAddress.normalize(command.email());
		if (!EmailAddress.isValid(email)) {
			return invalid("email", "Enter a valid email address.");
		}
		var password = PasswordPolicy.validate(command.password());
		if (password.isFailure()) {
			return invalid("password", password.getError());
		}

		UUID tenantId = caller.organization().tenantId();
		int maxUsers = caller.subscription().maxUsers();
		NewUser newUser = new NewUser(UUID.randomUUID(), email, fullName, hasher.hash(command.password()), role);
		var created = users.createWithinLimit(tenantId, newUser, maxUsers);
		if (created.isFailure()) {
			return Result.failure(switch (created.getError()) {
				case EMAIL_TAKEN -> new MemberError.EmailTaken();
				case LIMIT_REACHED -> new MemberError.UserLimitReached(maxUsers);
			});
		}
		organizations.recordUserAdded(caller.organization().id(), caller.user().userCode(), newUser.userCode(), role);
		return Result.success(new Member(newUser.userCode(), fullName, email, role, true, null));
	}

	/** OWNER grants ADMIN or MEMBER, ADMIN grants MEMBER, nobody grants OWNER (one owner per organization). */
	static boolean mayGrant(OrganizationRole grantor, OrganizationRole requested) {
		return switch (grantor) {
			case OWNER -> requested == OrganizationRole.ADMIN || requested == OrganizationRole.MEMBER;
			case ADMIN -> requested == OrganizationRole.MEMBER;
			case MEMBER -> false;
		};
	}

	private static OrganizationRole parseRole(String raw) {
		if (raw == null) {
			return null;
		}
		try {
			return OrganizationRole.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException _) {
			return null;
		}
	}

	private static Result<Member, MemberError> invalid(String field, String message) {
		return Result.failure(new MemberError.InvalidUserData(field, message));
	}

}

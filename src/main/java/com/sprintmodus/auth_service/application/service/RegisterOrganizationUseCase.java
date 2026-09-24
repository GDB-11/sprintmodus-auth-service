package com.sprintmodus.auth_service.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sprintmodus.auth_service.application.dto.RegisterOrganizationCommand;
import com.sprintmodus.auth_service.application.dto.RegisteredOrganization;
import com.sprintmodus.auth_service.application.error.RegistrationError;
import com.sprintmodus.auth_service.application.error.RegistrationError.EmailAlreadyRegistered;
import com.sprintmodus.auth_service.application.error.RegistrationError.InvalidOrganizationCode;
import com.sprintmodus.auth_service.application.error.RegistrationError.InvalidRegistrationData;
import com.sprintmodus.auth_service.application.error.RegistrationError.OrganizationCodeTaken;
import com.sprintmodus.auth_service.application.port.external.PasswordHasher;
import com.sprintmodus.auth_service.application.port.external.TenantProvisioner;
import com.sprintmodus.auth_service.application.port.external.TenantProvisioner.TenantDatabase;
import com.sprintmodus.auth_service.application.port.persistence.OrganizationRepository;
import com.sprintmodus.auth_service.application.port.persistence.TenantUserRepository;
import com.sprintmodus.auth_service.domain.model.OrganizationCode;
import com.sprintmodus.auth_service.domain.model.OrganizationRole;
import com.sprintmodus.auth_service.domain.model.Plan;
import com.sprintmodus.auth_service.domain.service.EmailAddress;
import com.sprintmodus.auth_service.domain.service.PasswordPolicy;
import com.sprintmodus.common_lib.result.Result;

/**
 * Onboards a new tenant: validates the request, provisions the tenant database, creates the owner in it, then records
 * the organization and its default subscription in the master DB.
 * <p>
 * The master rows are written <em>last</em>. Until then nothing points at the new database, so if any step fails the
 * database is simply dropped and no orphan {@code Organization} row is left behind. Business problems come back as a
 * {@link Result}; infrastructure failures are rethrown after the rollback and end up as HTTP 500.
 */
@Service
public class RegisterOrganizationUseCase {

	private static final Plan DEFAULT_PLAN = Plan.FREE;

	private static final int MAX_NAME_LENGTH = 255;

	private final OrganizationRepository organizations;

	private final TenantUserRepository users;

	private final TenantProvisioner provisioner;

	private final PasswordHasher hasher;

	public RegisterOrganizationUseCase(OrganizationRepository organizations, TenantUserRepository users,
			TenantProvisioner provisioner, PasswordHasher hasher) {
		this.organizations = organizations;
		this.users = users;
		this.provisioner = provisioner;
		this.hasher = hasher;
	}

	/** The request after validation and normalization. */
	private record Valid(OrganizationCode code, String organizationName, String fullName, String email, String password) {
	}

	public Result<RegisteredOrganization, RegistrationError> execute(RegisterOrganizationCommand command) {
		return validate(command).flatMap(this::ensureAvailable).flatMap(this::provisionAndRecord);
	}

	private Result<Valid, RegistrationError> validate(RegisterOrganizationCommand command) {
		String organizationName = command.organizationName() == null ? "" : command.organizationName().trim();
		if (organizationName.isEmpty() || organizationName.length() > MAX_NAME_LENGTH) {
			return invalid("organizationName", "Enter the organization name.");
		}
		String fullName = command.fullName() == null ? "" : command.fullName().trim();
		if (fullName.isEmpty() || fullName.length() > MAX_NAME_LENGTH) {
			return invalid("fullName", "Enter your full name.");
		}
		String email = EmailAddress.normalize(command.email());
		if (!EmailAddress.isValid(email)) {
			return invalid("email", "Enter a valid email address.");
		}
		Result<OrganizationCode, RegistrationError> code = OrganizationCode.parse(command.organizationCode())
				.mapError(InvalidOrganizationCode::new);
		if (code.isFailure()) {
			return Result.failure(code.getError());
		}
		var password = PasswordPolicy.validate(command.password());
		if (password.isFailure()) {
			return invalid("password", password.getError());
		}
		return Result.success(new Valid(code.getValue(), organizationName, fullName, email, command.password()));
	}

	private static Result<Valid, RegistrationError> invalid(String field, String message) {
		return Result.failure(new InvalidRegistrationData(field, message));
	}

	private Result<Valid, RegistrationError> ensureAvailable(Valid request) {
		if (organizations.existsByCode(request.code().value())) {
			return Result.failure(new OrganizationCodeTaken());
		}
		if (organizations.existsByEmail(request.email())) {
			return Result.failure(new EmailAlreadyRegistered());
		}
		return Result.success(request);
	}

	private Result<RegisteredOrganization, RegistrationError> provisionAndRecord(Valid request) {
		UUID tenantId = UUID.randomUUID();
		UUID ownerCode = UUID.randomUUID();

		TenantDatabase database = provisioner.create(tenantId);
		try {
			users.create(tenantId, new TenantUserRepository.NewUser(ownerCode, request.email(), request.fullName(),
					hasher.hash(request.password()), OrganizationRole.OWNER));

			var registered = organizations.register(new OrganizationRepository.Registration(tenantId,
					request.code().value(), request.organizationName(), request.email(), database, DEFAULT_PLAN, ownerCode));
			if (registered.isFailure()) {
				// Someone registered the same code or email between the check above and now
				provisioner.drop(tenantId);
				return Result.failure(registered.getError() == OrganizationRepository.Duplicate.CODE
						? new OrganizationCodeTaken() : new EmailAlreadyRegistered());
			}
			return Result.success(new RegisteredOrganization(request.code().value(), request.organizationName(), DEFAULT_PLAN));
		}
		catch (RuntimeException failure) {
			provisioner.drop(tenantId);
			throw failure;
		}
	}

}

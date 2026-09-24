package com.sprintmodus.auth_service.application.service;

import org.springframework.stereotype.Service;

import com.sprintmodus.auth_service.application.error.RegistrationError;
import com.sprintmodus.auth_service.application.error.RegistrationError.InvalidOrganizationCode;
import com.sprintmodus.auth_service.application.error.RegistrationError.OrganizationCodeTaken;
import com.sprintmodus.auth_service.application.port.persistence.OrganizationRepository;
import com.sprintmodus.auth_service.domain.model.OrganizationCode;
import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.common_lib.result.Unit;

/** Lets the registration form check a code before submitting, with the same rules onboarding applies. */
@Service
public class CheckOrganizationCodeUseCase {

	private final OrganizationRepository organizations;

	public CheckOrganizationCodeUseCase(OrganizationRepository organizations) {
		this.organizations = organizations;
	}

	/** Success means the code is well formed, not reserved and not taken. */
	public Result<Unit, RegistrationError> execute(String rawCode) {
		return OrganizationCode.parse(rawCode)
				.<RegistrationError>mapError(InvalidOrganizationCode::new)
				.flatMap(code -> organizations.existsByCode(code.value())
						? Result.<Unit, RegistrationError>failure(new OrganizationCodeTaken())
						: Result.<Unit, RegistrationError>success(Unit.VALUE));
	}

}

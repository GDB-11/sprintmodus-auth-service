package com.sprintmodus.auth_service.application.error;

import com.sprintmodus.auth_service.domain.model.OrganizationCode;
import com.sprintmodus.common_lib.result.ApplicationError;

/** Why an organization could not be registered (or a code is not available). */
public sealed interface RegistrationError extends ApplicationError {

	/** A field of the request is missing or malformed; {@code message} says what to fix. */
	record InvalidRegistrationData(String field, String message) implements RegistrationError {

		@Override
		public String code() {
			return "INVALID_REGISTRATION_DATA";
		}

	}

	record InvalidOrganizationCode(OrganizationCode.Problem problem) implements RegistrationError {

		@Override
		public String code() {
			return problem == OrganizationCode.Problem.RESERVED ? "ORGANIZATION_CODE_RESERVED"
					: "INVALID_ORGANIZATION_CODE";
		}

		@Override
		public String message() {
			return switch (problem) {
				case RESERVED -> "This organization code is reserved.";
				case INVALID_FORMAT -> "Use 3 to 30 letters, digits or hyphens, without a hyphen at the start or end.";
			};
		}

	}

	record OrganizationCodeTaken() implements RegistrationError {

		@Override
		public String code() {
			return "ORGANIZATION_CODE_TAKEN";
		}

		@Override
		public String message() {
			return "This organization code is already taken.";
		}

	}

	record EmailAlreadyRegistered() implements RegistrationError {

		@Override
		public String code() {
			return "EMAIL_ALREADY_REGISTERED";
		}

		@Override
		public String message() {
			return "An organization is already registered with this email.";
		}

	}

}

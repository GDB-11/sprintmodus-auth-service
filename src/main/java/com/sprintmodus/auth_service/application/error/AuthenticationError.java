package com.sprintmodus.auth_service.application.error;

import com.sprintmodus.common_lib.result.ApplicationError;

/** Why a login was refused. */
public sealed interface AuthenticationError extends ApplicationError {

	/**
	 * Unknown or inactive organization, unknown or inactive user, wrong password. They are deliberately one error, so a
	 * caller cannot tell which part was wrong.
	 */
	record InvalidCredentials() implements AuthenticationError {

		@Override
		public String code() {
			return "INVALID_CREDENTIALS";
		}

		@Override
		public String message() {
			return "Invalid email, password or organization code.";
		}

	}

	/** The organization exists but its subscription is inactive or expired. */
	record SubscriptionInactive() implements AuthenticationError {

		@Override
		public String code() {
			return "SUBSCRIPTION_INACTIVE";
		}

		@Override
		public String message() {
			return "This organization's subscription is inactive or has expired.";
		}

	}

}

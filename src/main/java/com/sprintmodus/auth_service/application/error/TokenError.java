package com.sprintmodus.auth_service.application.error;

import com.sprintmodus.common_lib.result.ApplicationError;

/** Why a token was refused when validating or refreshing it. */
public sealed interface TokenError extends ApplicationError {

	/** Missing, malformed, wrongly signed, or no longer matching a live organization and user. */
	record InvalidToken() implements TokenError {

		@Override
		public String code() {
			return "INVALID_TOKEN";
		}

		@Override
		public String message() {
			return "The token is invalid.";
		}

	}

	record ExpiredToken() implements TokenError {

		@Override
		public String code() {
			return "TOKEN_EXPIRED";
		}

		@Override
		public String message() {
			return "The token has expired.";
		}

	}

	/** A refresh was refused because the organization's subscription is no longer valid. */
	record SubscriptionInactive() implements TokenError {

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

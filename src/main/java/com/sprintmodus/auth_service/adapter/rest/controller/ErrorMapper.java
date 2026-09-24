package com.sprintmodus.auth_service.adapter.rest.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.sprintmodus.auth_service.application.error.AuthenticationError;
import com.sprintmodus.auth_service.application.error.RegistrationError;
import com.sprintmodus.auth_service.application.error.TokenError;
import com.sprintmodus.common_lib.result.ApplicationError;
import com.sprintmodus.common_lib.web.ErrorResponse;

/**
 * The HTTP status of every business error. The switches are exhaustive over the sealed hierarchies, so adding an error
 * type without deciding its status does not compile.
 */
final class ErrorMapper {

	private ErrorMapper() {
	}

	static <T> ResponseEntity<T> toResponse(ApplicationError error) {
		return respond(status(error), error);
	}

	static HttpStatus status(ApplicationError error) {
		return switch (error) {
			case AuthenticationError authentication -> status(authentication);
			case RegistrationError registration -> status(registration);
			case TokenError token -> status(token);
			default -> HttpStatus.INTERNAL_SERVER_ERROR;
		};
	}

	private static HttpStatus status(AuthenticationError error) {
		return switch (error) {
			case AuthenticationError.InvalidCredentials _ -> HttpStatus.UNAUTHORIZED;
			case AuthenticationError.SubscriptionInactive _ -> HttpStatus.PAYMENT_REQUIRED;
		};
	}

	private static HttpStatus status(RegistrationError error) {
		return switch (error) {
			case RegistrationError.InvalidRegistrationData _, RegistrationError.InvalidOrganizationCode _ ->
				HttpStatus.BAD_REQUEST;
			case RegistrationError.OrganizationCodeTaken _, RegistrationError.EmailAlreadyRegistered _ ->
				HttpStatus.CONFLICT;
		};
	}

	private static HttpStatus status(TokenError error) {
		return switch (error) {
			case TokenError.InvalidToken _, TokenError.ExpiredToken _ -> HttpStatus.UNAUTHORIZED;
			case TokenError.SubscriptionInactive _ -> HttpStatus.PAYMENT_REQUIRED;
		};
	}

	@SuppressWarnings("unchecked")
	private static <T> ResponseEntity<T> respond(HttpStatus status, ApplicationError error) {
		return (ResponseEntity<T>) ResponseEntity.status(status).body(new ErrorResponse(error.code(), error.message()));
	}

}

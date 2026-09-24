package com.sprintmodus.auth_service.adapter.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.sprintmodus.auth_service.application.error.AuthenticationError;
import com.sprintmodus.auth_service.application.error.RegistrationError;
import com.sprintmodus.auth_service.application.error.TokenError;
import com.sprintmodus.auth_service.domain.model.OrganizationCode;

class ErrorMapperTest {

	@Test
	void mapsAuthenticationErrors() {
		assertThat(ErrorMapper.status(new AuthenticationError.InvalidCredentials())).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(ErrorMapper.status(new AuthenticationError.SubscriptionInactive())).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
	}

	@Test
	void mapsRegistrationErrors() {
		assertThat(ErrorMapper.status(new RegistrationError.InvalidRegistrationData("email", "bad"))).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(ErrorMapper.status(new RegistrationError.InvalidOrganizationCode(OrganizationCode.Problem.RESERVED)))
				.isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(ErrorMapper.status(new RegistrationError.OrganizationCodeTaken())).isEqualTo(HttpStatus.CONFLICT);
		assertThat(ErrorMapper.status(new RegistrationError.EmailAlreadyRegistered())).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void mapsTokenErrors() {
		assertThat(ErrorMapper.status(new TokenError.InvalidToken())).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(ErrorMapper.status(new TokenError.ExpiredToken())).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(ErrorMapper.status(new TokenError.SubscriptionInactive())).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
	}

	@Test
	void errorMessagesDoNotDependOnWhichPartOfTheLoginWasWrong() {
		assertThat(new AuthenticationError.InvalidCredentials().message()).doesNotContainIgnoringCase("password is")
				.doesNotContainIgnoringCase("not found").doesNotContainIgnoringCase("unknown");
	}

}

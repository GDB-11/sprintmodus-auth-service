package com.sprintmodus.auth_service.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sprintmodus.auth_service.application.dto.TokenClaims;
import com.sprintmodus.auth_service.application.error.RegistrationError;
import com.sprintmodus.auth_service.application.error.TokenError;
import com.sprintmodus.auth_service.application.service.CheckOrganizationCodeUseCase;
import com.sprintmodus.auth_service.application.service.ValidateTokenUseCase;
import com.sprintmodus.auth_service.domain.model.OrganizationRole;
import com.sprintmodus.auth_service.domain.model.Plan;
import com.sprintmodus.auth_service.support.Fakes;
import com.sprintmodus.common_lib.result.Result;

class CodeAndTokenUseCasesTest {

	@Test
	void aFreeWellFormedCodeIsAvailable() {
		var check = new CheckOrganizationCodeUseCase(new Fakes.Organizations());

		assertThat(check.execute("Acme-2").isSuccess()).isTrue();
	}

	@Test
	void reportsWhyACodeIsNotAvailable() {
		var organizations = new Fakes.Organizations();
		organizations.add("acme", UUID.randomUUID(), true);
		var check = new CheckOrganizationCodeUseCase(organizations);

		assertThat(check.execute("acme").getError()).isInstanceOf(RegistrationError.OrganizationCodeTaken.class);
		assertThat(check.execute("ACME").getError()).isInstanceOf(RegistrationError.OrganizationCodeTaken.class);
		assertThat(check.execute("api").getError().code()).isEqualTo("ORGANIZATION_CODE_RESERVED");
		assertThat(check.execute("a").getError().code()).isEqualTo("INVALID_ORGANIZATION_CODE");
		assertThat(check.execute(null).getError().code()).isEqualTo("INVALID_ORGANIZATION_CODE");
	}

	@Test
	void validatesATokenWithoutTouchingAnyDatabase() {
		var tokens = new Fakes.Tokens();
		var claims = new TokenClaims(UUID.randomUUID(), "ana@acme.io", UUID.randomUUID(), "acme", OrganizationRole.OWNER,
				Plan.FREE, 1, 5, 100);
		tokens.register("good", Result.success(new TokenClaims.Verified(claims, Instant.parse("2030-01-01T00:00:00Z"))));
		var validate = new ValidateTokenUseCase(tokens);

		assertThat(validate.execute("good").getValue().claims()).isEqualTo(claims);
		assertThat(validate.execute("bad").getError()).isInstanceOf(TokenError.InvalidToken.class);
	}

}

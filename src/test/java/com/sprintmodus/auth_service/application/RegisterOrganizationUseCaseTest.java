package com.sprintmodus.auth_service.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sprintmodus.auth_service.application.dto.RegisterOrganizationCommand;
import com.sprintmodus.auth_service.application.dto.RegisteredOrganization;
import com.sprintmodus.auth_service.application.error.RegistrationError;
import com.sprintmodus.auth_service.application.port.external.TenantProvisioner.TenantProvisioningException;
import com.sprintmodus.auth_service.application.port.persistence.OrganizationRepository;
import com.sprintmodus.auth_service.application.service.RegisterOrganizationUseCase;
import com.sprintmodus.auth_service.domain.model.OrganizationRole;
import com.sprintmodus.auth_service.domain.model.Plan;
import com.sprintmodus.auth_service.support.Fakes;
import com.sprintmodus.common_lib.result.Result;

class RegisterOrganizationUseCaseTest {

	private final Fakes.Organizations organizations = new Fakes.Organizations();

	private final Fakes.TenantUsers users = new Fakes.TenantUsers();

	private final Fakes.Provisioner provisioner = new Fakes.Provisioner();

	private RegisterOrganizationUseCase register;

	@BeforeEach
	void setUp() {
		register = new RegisterOrganizationUseCase(organizations, users, provisioner, new Fakes.Hasher());
	}

	private static RegisterOrganizationCommand command(String name, String code, String fullName, String email, String password) {
		return new RegisterOrganizationCommand(name, code, fullName, email, password);
	}

	private static RegisterOrganizationCommand valid() {
		return command("Acme Inc", "Acme", "Ana Diaz", "Ana@Acme.io", "correct-horse");
	}

	@Test
	void registersTheOrganizationWithItsOwnerOnTheFreePlan() {
		Result<RegisteredOrganization, RegistrationError> result = register.execute(valid());

		assertThat(result.getValue()).isEqualTo(new RegisteredOrganization("acme", "Acme Inc", Plan.FREE));
		assertThat(provisioner.created).hasSize(1);
		assertThat(provisioner.dropped).isEmpty();

		var owner = users.created.getFirst();
		assertThat(owner.role()).isEqualTo(OrganizationRole.OWNER);
		assertThat(owner.email()).isEqualTo("ana@acme.io");
		assertThat(owner.fullName()).isEqualTo("Ana Diaz");
		assertThat(owner.passwordHash()).isEqualTo("hashed:correct-horse").doesNotContain("Ana");

		OrganizationRepository.Registration recorded = organizations.registrations.getFirst();
		assertThat(recorded.tenantId()).isEqualTo(provisioner.created.getFirst());
		assertThat(recorded.code()).isEqualTo("acme");
		assertThat(recorded.email()).isEqualTo("ana@acme.io");
		assertThat(recorded.plan()).isEqualTo(Plan.FREE);
		assertThat(recorded.ownerUserCode()).isEqualTo(owner.userCode());
		assertThat(recorded.database().encryptedPassword()).isEqualTo("encrypted-secret");
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', nullValues = "NULL", textBlock = """
			NULL | acme | Ana | ana@acme.io | correct-horse | organizationName
			'  ' | acme | Ana | ana@acme.io | correct-horse | organizationName
			Acme | acme | NULL | ana@acme.io | correct-horse | fullName
			Acme | acme | Ana | not-an-email | correct-horse | email
			Acme | acme | Ana | NULL | correct-horse | email
			Acme | acme | Ana | ana@acme.io | short | password
			Acme | acme | Ana | ana@acme.io | NULL | password
			""")
	void rejectsInvalidData(String name, String code, String fullName, String email, String password, String field) {
		var result = register.execute(command(name, code, fullName, email, password));

		assertThat(result.getError()).isInstanceOfSatisfying(RegistrationError.InvalidRegistrationData.class,
				error -> assertThat(error.field()).isEqualTo(field));
		assertThat(provisioner.created).isEmpty();
	}

	@ParameterizedTest
	@CsvSource({ "ab,INVALID_ORGANIZATION_CODE", "-acme,INVALID_ORGANIZATION_CODE", "acme-,INVALID_ORGANIZATION_CODE",
			"ac me,INVALID_ORGANIZATION_CODE", "acme_inc,INVALID_ORGANIZATION_CODE", "admin,ORGANIZATION_CODE_RESERVED",
			"WWW,ORGANIZATION_CODE_RESERVED" })
	void rejectsMalformedAndReservedCodes(String code, String expectedErrorCode) {
		var result = register.execute(command("Acme", code, "Ana", "ana@acme.io", "correct-horse"));

		assertThat(result.getError().code()).isEqualTo(expectedErrorCode);
		assertThat(provisioner.created).isEmpty();
	}

	@Test
	void rejectsACodeThatIsTaken() {
		organizations.add("acme", UUID.randomUUID(), true);

		var result = register.execute(valid());

		assertThat(result.getError()).isInstanceOf(RegistrationError.OrganizationCodeTaken.class);
		assertThat(provisioner.created).isEmpty();
	}

	@Test
	void treatsCodesAsCaseInsensitive() {
		organizations.add("acme", UUID.randomUUID(), true);

		var result = register.execute(command("Acme", "ACME", "Ana", "new@acme.io", "correct-horse"));

		assertThat(result.getError()).isInstanceOf(RegistrationError.OrganizationCodeTaken.class);
	}

	@Test
	void rejectsAnEmailThatIsAlreadyRegistered() {
		organizations.add("other", UUID.randomUUID(), true);

		var result = register.execute(command("Acme", "acme", "Ana", "OTHER@example.com", "correct-horse"));

		assertThat(result.getError()).isInstanceOf(RegistrationError.EmailAlreadyRegistered.class);
		assertThat(provisioner.created).isEmpty();
	}

	@Test
	void dropsTheDatabaseWhenAnotherRegistrationWonTheRace() {
		organizations.raceLostOn = OrganizationRepository.Duplicate.CODE;

		var result = register.execute(valid());

		assertThat(result.getError()).isInstanceOf(RegistrationError.OrganizationCodeTaken.class);
		assertThat(provisioner.dropped).containsExactlyElementsOf(provisioner.created);
	}

	@Test
	void reportsAnEmailRaceAsAnEmailConflict() {
		organizations.raceLostOn = OrganizationRepository.Duplicate.EMAIL;

		assertThat(register.execute(valid()).getError()).isInstanceOf(RegistrationError.EmailAlreadyRegistered.class);
		assertThat(provisioner.dropped).hasSize(1);
	}

	@Test
	void dropsTheDatabaseAndRethrowsWhenCreatingTheOwnerFails() {
		users.failCreateWith = new IllegalStateException("tenant database unavailable");

		assertThatThrownBy(() -> register.execute(valid())).hasMessage("tenant database unavailable");

		assertThat(provisioner.dropped).containsExactlyElementsOf(provisioner.created);
		assertThat(organizations.registrations).as("nothing is recorded in the master DB").isEmpty();
	}

	@Test
	void leavesNothingBehindWhenProvisioningFails() {
		provisioner.failCreateWith = new TenantProvisioningException("cannot create", new RuntimeException());

		assertThatThrownBy(() -> register.execute(valid())).isInstanceOf(TenantProvisioningException.class);

		assertThat(users.created).isEmpty();
		assertThat(organizations.registrations).isEmpty();
	}

}

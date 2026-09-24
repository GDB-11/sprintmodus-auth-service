package com.sprintmodus.auth_service.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sprintmodus.auth_service.domain.model.OrganizationCode;
import com.sprintmodus.auth_service.domain.model.OrganizationSubscription;
import com.sprintmodus.auth_service.domain.model.Plan;
import com.sprintmodus.auth_service.domain.service.EmailAddress;
import com.sprintmodus.auth_service.domain.service.PasswordPolicy;

class DomainRulesTest {

	private static final Instant NOW = Instant.parse("2026-09-23T12:00:00Z");

	@ParameterizedTest
	@ValueSource(strings = { "acme", "ACME", "  acme  ", "a1b", "acme-corp", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "007" })
	void acceptsWellFormedCodesAndLowercasesThem(String raw) {
		var parsed = OrganizationCode.parse(raw);

		assertThat(parsed.isSuccess()).isTrue();
		assertThat(parsed.getValue().value()).isEqualTo(raw.trim().toLowerCase());
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "  ", "ab", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "-acme", "acme-", "ac me", "acme_inc", "acme.io", "ácme", "acme!" })
	void rejectsMalformedCodes(String raw) {
		assertThat(OrganizationCode.parse(raw).getError()).isEqualTo(OrganizationCode.Problem.INVALID_FORMAT);
	}

	@Test
	void rejectsNullAndReservedCodes() {
		assertThat(OrganizationCode.parse(null).getError()).isEqualTo(OrganizationCode.Problem.INVALID_FORMAT);
		assertThat(OrganizationCode.parse("Admin").getError()).isEqualTo(OrganizationCode.Problem.RESERVED);
		assertThat(OrganizationCode.parse("auth").getError()).isEqualTo(OrganizationCode.Problem.RESERVED);
		assertThat(OrganizationCode.parse("www").getError()).isEqualTo(OrganizationCode.Problem.RESERVED);
	}

	@Test
	void passwordsNeedEightCharactersAndFitBcrypt() {
		assertThat(PasswordPolicy.validate("1234567").isFailure()).isTrue();
		assertThat(PasswordPolicy.validate("12345678").isSuccess()).isTrue();
		assertThat(PasswordPolicy.validate(null).isFailure()).isTrue();
		assertThat(PasswordPolicy.validate("a".repeat(72)).isSuccess()).isTrue();
		assertThat(PasswordPolicy.validate("a".repeat(73)).isFailure()).isTrue();
		// 37 two-byte characters are 74 bytes: within 72 characters but over BCrypt's byte limit
		assertThat(PasswordPolicy.validate("é".repeat(37)).isFailure()).isTrue();
	}

	@Test
	void emailsAreNormalizedAndChecked() {
		assertThat(EmailAddress.normalize("  Ana@Acme.IO ")).isEqualTo("ana@acme.io");
		assertThat(EmailAddress.normalize(null)).isEmpty();
		assertThat(EmailAddress.isValid("ana@acme.io")).isTrue();
		assertThat(EmailAddress.isValid("ana@acme")).isFalse();
		assertThat(EmailAddress.isValid("ana acme@x.io")).isFalse();
		assertThat(EmailAddress.isValid("@acme.io")).isFalse();
		assertThat(EmailAddress.isValid("a@b.io".repeat(60))).isFalse();
	}

	@Test
	void aSubscriptionIsUsableWhileActiveAndUnexpired() {
		assertThat(subscription(null, true).isUsableAt(NOW)).isTrue();
		assertThat(subscription(NOW.plusSeconds(1), true).isUsableAt(NOW)).isTrue();
		assertThat(subscription(NOW, true).isUsableAt(NOW)).as("expires exactly now").isFalse();
		assertThat(subscription(NOW.minusSeconds(1), true).isUsableAt(NOW)).isFalse();
		assertThat(subscription(null, false).isUsableAt(NOW)).isFalse();
	}

	@Test
	void planLimitsMatchTheDocumentedTiers() {
		assertThat(Plan.FREE.maxProjects()).isEqualTo(1);
		assertThat(Plan.FREE.maxUsers()).isEqualTo(5);
		assertThat(Plan.FREE.maxStorageMB()).isEqualTo(100);
		assertThat(Plan.PRO.maxProjects()).isEqualTo(10);
		assertThat(Plan.PRO.maxUsers()).isEqualTo(50);
		assertThat(Plan.ENTERPRISE.maxStorageMB()).isEqualTo(100_000);
	}

	private static OrganizationSubscription subscription(Instant expiresAt, boolean active) {
		return new OrganizationSubscription(1, Plan.FREE, 1, 5, 100, expiresAt, active);
	}

}

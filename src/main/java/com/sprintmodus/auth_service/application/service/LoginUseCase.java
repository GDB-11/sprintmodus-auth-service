package com.sprintmodus.auth_service.application.service;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sprintmodus.auth_service.application.dto.AuthenticatedSession;
import com.sprintmodus.auth_service.application.dto.LoginCommand;
import com.sprintmodus.auth_service.application.error.AuthenticationError;
import com.sprintmodus.auth_service.application.error.AuthenticationError.InvalidCredentials;
import com.sprintmodus.auth_service.application.error.AuthenticationError.SubscriptionInactive;
import com.sprintmodus.auth_service.application.port.external.PasswordHasher;
import com.sprintmodus.auth_service.application.port.persistence.OrganizationRepository;
import com.sprintmodus.auth_service.application.port.persistence.SubscriptionRepository;
import com.sprintmodus.auth_service.application.port.persistence.TenantUserRepository;
import com.sprintmodus.auth_service.domain.model.Organization;
import com.sprintmodus.auth_service.domain.model.OrganizationCode;
import com.sprintmodus.auth_service.domain.model.OrganizationSubscription;
import com.sprintmodus.auth_service.domain.model.TenantUser;
import com.sprintmodus.auth_service.domain.service.EmailAddress;
import com.sprintmodus.common_lib.result.Result;

/**
 * Logs a user in: the master DB resolves the organization and checks its subscription (on every login, before the
 * tenant DB is touched), then the tenant DB checks the user's credentials.
 * <p>
 * An unknown or inactive organization, an unknown or inactive user and a wrong password all fail identically, and each
 * of them still pays for one password hash comparison so response time does not tell them apart.
 */
@Service
public class LoginUseCase {

	private final OrganizationRepository organizations;

	private final SubscriptionRepository subscriptions;

	private final TenantUserRepository users;

	private final PasswordHasher hasher;

	private final SessionIssuer sessions;

	private final Clock clock;

	/** A hash nobody knows the password of, compared against when there is no real hash to compare with. */
	private final String unusableHash;

	public LoginUseCase(OrganizationRepository organizations, SubscriptionRepository subscriptions,
			TenantUserRepository users, PasswordHasher hasher, SessionIssuer sessions, Clock clock) {
		this.organizations = organizations;
		this.subscriptions = subscriptions;
		this.users = users;
		this.hasher = hasher;
		this.sessions = sessions;
		this.clock = clock;
		this.unusableHash = hasher.hash(UUID.randomUUID().toString());
	}

	public Result<AuthenticatedSession, AuthenticationError> execute(LoginCommand command) {
		String password = command.password() == null ? "" : command.password();

		Optional<Organization> organization = organizations.findByCode(OrganizationCode.normalize(command.organizationCode()))
				.filter(Organization::active);
		if (organization.isEmpty()) {
			return rejectCredentials(password);
		}

		Optional<OrganizationSubscription> subscription = subscriptions.findByOrganizationId(organization.get().id())
				.filter(found -> found.isUsableAt(clock.instant()));
		if (subscription.isEmpty()) {
			return Result.failure(new SubscriptionInactive());
		}

		Optional<TenantUser> user = users.findByEmail(organization.get().tenantId(), EmailAddress.normalize(command.email()))
				.filter(TenantUser::active);
		if (user.isEmpty() || !hasher.matches(password, user.get().passwordHash())) {
			return rejectCredentials(user.isEmpty() ? password : null);
		}

		users.recordLogin(organization.get().tenantId(), user.get().userCode(), clock.instant());
		return Result.success(sessions.issue(organization.get(), subscription.get(), user.get()));
	}

	/**
	 * @param passwordToBurnTimeOn compared against the unusable hash to match the cost of a real check; {@code null}
	 * when a real comparison already happened
	 */
	private Result<AuthenticatedSession, AuthenticationError> rejectCredentials(String passwordToBurnTimeOn) {
		if (passwordToBurnTimeOn != null) {
			hasher.matches(passwordToBurnTimeOn, unusableHash);
		}
		return Result.failure(new InvalidCredentials());
	}

}

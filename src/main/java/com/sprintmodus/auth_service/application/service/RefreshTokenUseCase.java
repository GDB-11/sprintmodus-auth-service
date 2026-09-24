package com.sprintmodus.auth_service.application.service;

import java.time.Clock;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.sprintmodus.auth_service.application.dto.AuthenticatedSession;
import com.sprintmodus.auth_service.application.dto.TokenClaims;
import com.sprintmodus.auth_service.application.error.TokenError;
import com.sprintmodus.auth_service.application.error.TokenError.InvalidToken;
import com.sprintmodus.auth_service.application.error.TokenError.SubscriptionInactive;
import com.sprintmodus.auth_service.application.port.external.TokenProvider;
import com.sprintmodus.auth_service.application.port.persistence.OrganizationRepository;
import com.sprintmodus.auth_service.application.port.persistence.SubscriptionRepository;
import com.sprintmodus.auth_service.application.port.persistence.TenantUserRepository;
import com.sprintmodus.auth_service.domain.model.Organization;
import com.sprintmodus.auth_service.domain.model.OrganizationSubscription;
import com.sprintmodus.auth_service.domain.model.TenantUser;
import com.sprintmodus.common_lib.result.Result;

/**
 * Swaps a still-valid token for a fresh one. The organization, its subscription and the user are looked up again, so a
 * refresh picks up plan changes and stops working for a deactivated organization or user. An expired token cannot be
 * refreshed: the user logs in again.
 */
@Service
public class RefreshTokenUseCase {

	private final TokenProvider tokens;

	private final OrganizationRepository organizations;

	private final SubscriptionRepository subscriptions;

	private final TenantUserRepository users;

	private final SessionIssuer sessions;

	private final Clock clock;

	public RefreshTokenUseCase(TokenProvider tokens, OrganizationRepository organizations,
			SubscriptionRepository subscriptions, TenantUserRepository users, SessionIssuer sessions, Clock clock) {
		this.tokens = tokens;
		this.organizations = organizations;
		this.subscriptions = subscriptions;
		this.users = users;
		this.sessions = sessions;
		this.clock = clock;
	}

	public Result<AuthenticatedSession, TokenError> execute(String token) {
		return tokens.verify(token).flatMap(verified -> refresh(verified.claims()));
	}

	private Result<AuthenticatedSession, TokenError> refresh(TokenClaims claims) {
		// The tenant id in the token must belong to the organization the token names
		Optional<Organization> organization = organizations.findByCode(claims.organizationCode())
				.filter(found -> found.active() && found.tenantId().equals(claims.tenantId()));
		if (organization.isEmpty()) {
			return Result.failure(new InvalidToken());
		}

		Optional<OrganizationSubscription> subscription = subscriptions.findByOrganizationId(organization.get().id())
				.filter(found -> found.isUsableAt(clock.instant()));
		if (subscription.isEmpty()) {
			return Result.failure(new SubscriptionInactive());
		}

		Optional<TenantUser> user = users.findByCode(organization.get().tenantId(), claims.userCode())
				.filter(TenantUser::active);
		if (user.isEmpty()) {
			return Result.failure(new InvalidToken());
		}

		return Result.success(sessions.issue(organization.get(), subscription.get(), user.get()));
	}

}

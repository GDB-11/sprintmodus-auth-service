package com.sprintmodus.auth_service.application.service;

import org.springframework.stereotype.Component;

import com.sprintmodus.auth_service.application.dto.AuthenticatedSession;
import com.sprintmodus.auth_service.application.dto.TokenClaims;
import com.sprintmodus.auth_service.application.port.external.TokenProvider;
import com.sprintmodus.auth_service.domain.model.Organization;
import com.sprintmodus.auth_service.domain.model.OrganizationSubscription;
import com.sprintmodus.auth_service.domain.model.TenantUser;

/** Builds the token and session data for a user who has already been authenticated. Shared by login and refresh. */
@Component
public class SessionIssuer {

	private final TokenProvider tokens;

	public SessionIssuer(TokenProvider tokens) {
		this.tokens = tokens;
	}

	public AuthenticatedSession issue(Organization organization, OrganizationSubscription subscription, TenantUser user) {
		TokenClaims claims = new TokenClaims(user.userCode(), user.email(), organization.tenantId(), organization.code(),
				user.role(), subscription.plan(), subscription.maxProjects(), subscription.maxUsers(),
				subscription.maxStorageMB());
		return new AuthenticatedSession(tokens.generate(claims),
				new AuthenticatedSession.UserSummary(user.userCode(), user.email(), user.fullName(), user.role()),
				new AuthenticatedSession.OrganizationSummary(organization.tenantId(), organization.code(),
						organization.name()),
				new AuthenticatedSession.SubscriptionSummary(subscription.plan(), subscription.maxProjects(),
						subscription.maxUsers(), subscription.maxStorageMB()));
	}

}

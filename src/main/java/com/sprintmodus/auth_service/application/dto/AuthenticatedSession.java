package com.sprintmodus.auth_service.application.dto;

import java.util.UUID;

import com.sprintmodus.auth_service.domain.model.OrganizationRole;
import com.sprintmodus.auth_service.domain.model.Plan;

/** A signed-in user: the token plus the data a client needs to render the session. */
public record AuthenticatedSession(String token, UserSummary user, OrganizationSummary organization,
		SubscriptionSummary subscription) {

	public record UserSummary(UUID id, String email, String fullName, OrganizationRole role) {
	}

	/** {@code id} is the tenant id, the same value as the {@code tenantId} claim. */
	public record OrganizationSummary(UUID id, String code, String name) {
	}

	public record SubscriptionSummary(Plan plan, int maxProjects, int maxUsers, int maxStorageMB) {
	}

}

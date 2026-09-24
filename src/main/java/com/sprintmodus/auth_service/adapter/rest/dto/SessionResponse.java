package com.sprintmodus.auth_service.adapter.rest.dto;

import java.util.UUID;

import com.sprintmodus.auth_service.application.dto.AuthenticatedSession;
import com.sprintmodus.auth_service.domain.model.OrganizationRole;
import com.sprintmodus.auth_service.domain.model.Plan;

/** Response of login and refresh: the token plus what the client needs to render the session. */
public record SessionResponse(String token, User user, Organization organization, Subscription subscription) {

	public record User(UUID id, String email, String fullName, OrganizationRole role) {
	}

	/** {@code id} is the tenant id. */
	public record Organization(UUID id, String code, String name) {
	}

	public record Subscription(Plan plan, int maxProjects, int maxUsers, int maxStorageMB) {
	}

	public static SessionResponse from(AuthenticatedSession session) {
		return new SessionResponse(session.token(),
				new User(session.user().id(), session.user().email(), session.user().fullName(), session.user().role()),
				new Organization(session.organization().id(), session.organization().code(), session.organization().name()),
				new Subscription(session.subscription().plan(), session.subscription().maxProjects(),
						session.subscription().maxUsers(), session.subscription().maxStorageMB()));
	}

}

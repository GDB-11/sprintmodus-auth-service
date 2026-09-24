package com.sprintmodus.auth_service.domain.model;

import java.time.Instant;

/**
 * The plan and limits of an organization.
 *
 * @param expiresAt when the subscription stops being valid; {@code null} means it never expires
 */
public record OrganizationSubscription(long organizationId, Plan plan, int maxProjects, int maxUsers, int maxStorageMB,
		Instant expiresAt, boolean active) {

	/** Whether the subscription lets its organization sign in at {@code now}. */
	public boolean isUsableAt(Instant now) {
		return active && (expiresAt == null || expiresAt.isAfter(now));
	}

}

package com.sprintmodus.auth_service.application.dto;

import java.time.Instant;
import java.util.UUID;

import com.sprintmodus.auth_service.domain.model.OrganizationRole;
import com.sprintmodus.auth_service.domain.model.Plan;

/**
 * What the JWT says about its bearer. There is deliberately no database host or name: connection details are never
 * client-supplied, the backend derives the tenant database from {@code tenantId}.
 */
public record TokenClaims(UUID userCode, String email, UUID tenantId, String organizationCode, OrganizationRole role,
		Plan plan, int maxProjects, int maxUsers, int maxStorageMB) {

	/** Claims of a token that passed signature, issuer and expiry checks. */
	public record Verified(TokenClaims claims, Instant expiresAt) {
	}

}

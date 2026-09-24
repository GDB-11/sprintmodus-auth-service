package com.sprintmodus.auth_service.adapter.rest.dto;

import java.time.Instant;
import java.util.UUID;

import com.sprintmodus.auth_service.application.dto.TokenClaims;
import com.sprintmodus.auth_service.domain.model.OrganizationRole;
import com.sprintmodus.auth_service.domain.model.Plan;

public record ValidateTokenResponse(boolean valid, UUID userId, UUID tenantId, String organizationCode,
		OrganizationRole role, Plan plan, Instant expiresAt) {

	public static ValidateTokenResponse from(TokenClaims.Verified verified) {
		TokenClaims claims = verified.claims();
		return new ValidateTokenResponse(true, claims.userCode(), claims.tenantId(), claims.organizationCode(),
				claims.role(), claims.plan(), verified.expiresAt());
	}

}

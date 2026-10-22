package com.sprintmodus.auth_service.application.dto;

import java.time.Instant;
import java.util.UUID;

import com.sprintmodus.auth_service.domain.model.OrganizationRole;

/** A user of an organization as the members page shows it: never the password hash or the internal id. */
public record Member(UUID userCode, String fullName, String email, OrganizationRole role, boolean active,
		Instant lastLoginAt) {
}

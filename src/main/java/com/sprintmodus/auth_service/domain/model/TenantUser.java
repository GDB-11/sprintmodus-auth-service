package com.sprintmodus.auth_service.domain.model;

import java.util.UUID;

/** A member of an organization, as stored in that organization's own tenant database. */
public record TenantUser(UUID userCode, String email, String fullName, String passwordHash, OrganizationRole role,
		boolean active) {
}

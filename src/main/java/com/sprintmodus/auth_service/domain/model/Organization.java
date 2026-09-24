package com.sprintmodus.auth_service.domain.model;

import java.util.UUID;

/**
 * A tenant, as recorded in the master database.
 *
 * @param id internal master DB id; never exposed to clients
 * @param tenantId opaque UUID that names the tenant DB and travels in the JWT
 * @param code short login code chosen by the owner, always lowercase
 */
public record Organization(long id, UUID tenantId, String code, String name, String email, boolean active) {
}

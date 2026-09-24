package com.sprintmodus.auth_service.domain.model;

/** Organization-level role of a user. Per-work-item roles (DEV, QA, ...) are a separate concept in the tenant DB. */
public enum OrganizationRole {

	OWNER,
	ADMIN,
	MEMBER

}

package com.sprintmodus.auth_service.infrastructure.persistence.tenant;

import java.util.UUID;

import com.sprintmodus.auth_service.domain.model.OrganizationRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Persistence model of a row of the tenant {@code User} table, as returned by {@link TenantUserQueries}. */
@Entity
@Table(name = "User")
class TenantUserEntity {

	@Id
	@Column(name = "UserId")
	Long id;

	@Column(name = "UserCode")
	UUID userCode;

	@Column(name = "Email")
	String email;

	@Column(name = "PasswordHash")
	String passwordHash;

	@Column(name = "FullName")
	String fullName;

	@Enumerated(EnumType.STRING)
	@Column(name = "Role")
	OrganizationRole role;

	@Column(name = "IsActive")
	boolean active;

}

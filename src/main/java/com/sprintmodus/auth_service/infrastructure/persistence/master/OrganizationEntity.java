package com.sprintmodus.auth_service.infrastructure.persistence.master;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Persistence model of a row of the master {@code Organization} table. It only describes the columns the native queries
 * in {@link OrganizationQueries} return; it is never used to generate SQL.
 */
@Entity
@Table(name = "Organization")
class OrganizationEntity {

	@Id
	@Column(name = "OrganizationId")
	Long id;

	@Column(name = "TenantId")
	UUID tenantId;

	@Column(name = "OrganizationCode")
	String code;

	@Column(name = "Name")
	String name;

	@Column(name = "Email")
	String email;

	@Column(name = "IsActive")
	boolean active;

}

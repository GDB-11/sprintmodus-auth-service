package com.sprintmodus.auth_service.infrastructure.persistence.master;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Persistence model of the master {@code AuditLog} table. It is only written, through {@link AuditLogQueries}. */
@Entity
@Table(name = "AuditLog")
class AuditLogEntity {

	@Id
	@Column(name = "AuditId")
	Long id;

}

package com.sprintmodus.auth_service.infrastructure.persistence.master;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Every SQL statement on {@code AuditLog}, written out (native queries only). */
interface AuditLogQueries extends Repository<AuditLogEntity, Long> {

	/**
	 * {@code changedBy} is the tenant user's code (no foreign key: users live in the tenant database). {@code newValue}
	 * is JSON text.
	 */
	@Modifying
	@Query(nativeQuery = true, value = """
			INSERT INTO AuditLog (OrganizationId, Action, EntityType, EntityId, ChangedBy, NewValue)
			VALUES (:organizationId, :action, :entityType, :entityId, UUID_TO_BIN(:changedBy), CAST(:newValue AS JSON))
			""")
	int insert(@Param("organizationId") long organizationId, @Param("action") String action,
			@Param("entityType") String entityType, @Param("entityId") long entityId,
			@Param("changedBy") String changedBy, @Param("newValue") String newValue);

}

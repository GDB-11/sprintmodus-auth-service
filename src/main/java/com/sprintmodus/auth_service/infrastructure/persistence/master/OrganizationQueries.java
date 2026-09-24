package com.sprintmodus.auth_service.infrastructure.persistence.master;

import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Every SQL statement on {@code Organization}, written out. The interface extends the bare {@link Repository} marker on
 * purpose, so no generated query (derived, JPQL, or an inherited {@code save}/{@code findById}) is available: each
 * method is a native query. UUIDs are {@code BINARY(16)}: read as the column, written with {@code UUID_TO_BIN()}.
 */
interface OrganizationQueries extends Repository<OrganizationEntity, Long> {

	/** OrganizationCode has a case-insensitive collation, so the match is too. */
	@Query(nativeQuery = true, value = """
			SELECT OrganizationId, TenantId, OrganizationCode, Name, Email, IsActive
			FROM Organization
			WHERE OrganizationCode = :code
			""")
	Optional<OrganizationEntity> findByCode(@Param("code") String code);

	@Query(nativeQuery = true, value = "SELECT COUNT(*) FROM Organization WHERE OrganizationCode = :code")
	long countByCode(@Param("code") String code);

	@Query(nativeQuery = true, value = "SELECT COUNT(*) FROM Organization WHERE Email = :email")
	long countByEmail(@Param("email") String email);

	@Modifying
	@Query(nativeQuery = true, value = """
			INSERT INTO Organization (TenantId, OrganizationCode, Name, Email, DatabaseHost, DatabaseName,
			                          DatabaseUsername, DatabasePassword)
			VALUES (UUID_TO_BIN(:tenantId), :code, :name, :email, :databaseHost, :databaseName, :databaseUsername,
			        :databasePassword)
			""")
	int insert(@Param("tenantId") String tenantId, @Param("code") String code, @Param("name") String name,
			@Param("email") String email, @Param("databaseHost") String databaseHost,
			@Param("databaseName") String databaseName, @Param("databaseUsername") String databaseUsername,
			@Param("databasePassword") String databasePassword);

	@Query(nativeQuery = true, value = "SELECT OrganizationId FROM Organization WHERE TenantId = UUID_TO_BIN(:tenantId)")
	long findIdByTenantId(@Param("tenantId") String tenantId);

}

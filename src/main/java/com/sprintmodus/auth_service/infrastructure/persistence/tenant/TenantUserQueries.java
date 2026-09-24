package com.sprintmodus.auth_service.infrastructure.persistence.tenant;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every SQL statement on the tenant {@code User} table, written out (native queries only). A soft-deleted user
 * ({@code DeletedAt} set) is treated as not existing. The tenant context must be set before a method is called: the
 * routing DataSource picks the database when the statement or its transaction asks for a connection.
 */
interface TenantUserQueries extends Repository<TenantUserEntity, Long> {

	@Query(nativeQuery = true, value = """
			SELECT UserId, UserCode, Email, PasswordHash, FullName, Role, IsActive
			FROM `User`
			WHERE Email = :email AND DeletedAt IS NULL
			""")
	Optional<TenantUserEntity> findByEmail(@Param("email") String email);

	@Query(nativeQuery = true, value = """
			SELECT UserId, UserCode, Email, PasswordHash, FullName, Role, IsActive
			FROM `User`
			WHERE UserCode = UUID_TO_BIN(:userCode) AND DeletedAt IS NULL
			""")
	Optional<TenantUserEntity> findByCode(@Param("userCode") String userCode);

	@Transactional
	@Modifying
	@Query(nativeQuery = true, value = "UPDATE `User` SET LastLoginAt = :at WHERE UserCode = UUID_TO_BIN(:userCode)")
	int updateLastLogin(@Param("userCode") String userCode, @Param("at") Instant at);

	@Transactional
	@Modifying
	@Query(nativeQuery = true, value = """
			INSERT INTO `User` (UserCode, Email, PasswordHash, FullName, Role)
			VALUES (UUID_TO_BIN(:userCode), :email, :passwordHash, :fullName, :role)
			""")
	int insert(@Param("userCode") String userCode, @Param("email") String email,
			@Param("passwordHash") String passwordHash, @Param("fullName") String fullName, @Param("role") String role);

}

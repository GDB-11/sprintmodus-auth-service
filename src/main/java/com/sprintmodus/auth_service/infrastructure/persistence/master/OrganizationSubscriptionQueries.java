package com.sprintmodus.auth_service.infrastructure.persistence.master;

import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Every SQL statement on {@code OrganizationSubscription}, written out (native queries only). */
interface OrganizationSubscriptionQueries extends Repository<OrganizationSubscriptionEntity, Long> {

	@Query(nativeQuery = true, value = """
			SELECT SubscriptionId, OrganizationId, PlanType, MaxProjects, MaxUsers, MaxStorageMB, ExpiresAt, IsActive
			FROM OrganizationSubscription
			WHERE OrganizationId = :organizationId
			""")
	Optional<OrganizationSubscriptionEntity> findByOrganizationId(@Param("organizationId") long organizationId);

	@Modifying
	@Query(nativeQuery = true, value = """
			INSERT INTO OrganizationSubscription (OrganizationId, PlanType, MaxProjects, MaxUsers, MaxStorageMB)
			VALUES (:organizationId, :plan, :maxProjects, :maxUsers, :maxStorageMB)
			""")
	int insert(@Param("organizationId") long organizationId, @Param("plan") String plan,
			@Param("maxProjects") int maxProjects, @Param("maxUsers") int maxUsers,
			@Param("maxStorageMB") int maxStorageMB);

	@Query(nativeQuery = true, value = "SELECT SubscriptionId FROM OrganizationSubscription WHERE OrganizationId = :organizationId")
	long findIdByOrganizationId(@Param("organizationId") long organizationId);

}

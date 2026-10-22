package com.sprintmodus.auth_service.application.port.persistence;

import java.util.Optional;

import com.sprintmodus.auth_service.domain.model.OrganizationSubscription;
import com.sprintmodus.auth_service.domain.model.Plan;

public interface SubscriptionRepository {

	Optional<OrganizationSubscription> findByOrganizationId(long organizationId);

	/** Moves the organization to {@code plan}: the plan's limits replace the old ones and any expiry is cleared. */
	OrganizationSubscription changePlan(long organizationId, Plan plan);

}

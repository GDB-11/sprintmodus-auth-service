package com.sprintmodus.auth_service.application.port.persistence;

import java.util.Optional;

import com.sprintmodus.auth_service.domain.model.OrganizationSubscription;

public interface SubscriptionRepository {

	Optional<OrganizationSubscription> findByOrganizationId(long organizationId);

}

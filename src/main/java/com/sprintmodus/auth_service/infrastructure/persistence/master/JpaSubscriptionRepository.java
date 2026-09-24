package com.sprintmodus.auth_service.infrastructure.persistence.master;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.sprintmodus.auth_service.application.port.persistence.SubscriptionRepository;
import com.sprintmodus.auth_service.domain.model.OrganizationSubscription;

@Repository
class JpaSubscriptionRepository implements SubscriptionRepository {

	private final OrganizationSubscriptionQueries subscriptions;

	JpaSubscriptionRepository(OrganizationSubscriptionQueries subscriptions) {
		this.subscriptions = subscriptions;
	}

	@Override
	public Optional<OrganizationSubscription> findByOrganizationId(long organizationId) {
		return subscriptions.findByOrganizationId(organizationId)
				.map(entity -> new OrganizationSubscription(entity.organizationId, entity.plan, entity.maxProjects,
						entity.maxUsers, entity.maxStorageMB, entity.expiresAt, entity.active));
	}

}

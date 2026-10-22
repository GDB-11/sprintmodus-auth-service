package com.sprintmodus.auth_service.infrastructure.persistence.master;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sprintmodus.auth_service.application.port.persistence.SubscriptionRepository;
import com.sprintmodus.auth_service.domain.model.OrganizationSubscription;
import com.sprintmodus.auth_service.domain.model.Plan;

@Repository
class JpaSubscriptionRepository implements SubscriptionRepository {

	private final OrganizationSubscriptionQueries subscriptions;

	private final TransactionTemplate transaction;

	JpaSubscriptionRepository(OrganizationSubscriptionQueries subscriptions,
			@Qualifier("masterTransactionManager") PlatformTransactionManager transactionManager) {
		this.subscriptions = subscriptions;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	@Override
	public Optional<OrganizationSubscription> findByOrganizationId(long organizationId) {
		return subscriptions.findByOrganizationId(organizationId)
				.map(entity -> new OrganizationSubscription(entity.organizationId, entity.plan, entity.maxProjects,
						entity.maxUsers, entity.maxStorageMB, entity.expiresAt, entity.active));
	}

	@Override
	public OrganizationSubscription changePlan(long organizationId, Plan plan) {
		transaction.executeWithoutResult(_ -> subscriptions.changePlan(organizationId, plan.name(), plan.maxProjects(),
				plan.maxUsers(), plan.maxStorageMB()));
		return findByOrganizationId(organizationId).orElseThrow();
	}

}

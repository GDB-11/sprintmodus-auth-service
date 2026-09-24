package com.sprintmodus.auth_service.infrastructure.persistence.master;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sprintmodus.auth_service.application.port.persistence.OrganizationRepository;
import com.sprintmodus.auth_service.domain.model.Organization;
import com.sprintmodus.common_lib.result.Result;

import tools.jackson.databind.json.JsonMapper;

/**
 * The {@link OrganizationRepository} port, implemented over the native queries of {@link OrganizationQueries},
 * {@link OrganizationSubscriptionQueries} and {@link AuditLogQueries}. It maps persistence rows to domain objects.
 */
@Repository
class JpaOrganizationRepository implements OrganizationRepository {

	private final OrganizationQueries organizations;

	private final OrganizationSubscriptionQueries subscriptions;

	private final AuditLogQueries auditLog;

	private final TransactionTemplate transaction;

	private final JsonMapper json;

	JpaOrganizationRepository(OrganizationQueries organizations, OrganizationSubscriptionQueries subscriptions,
			AuditLogQueries auditLog, @Qualifier("masterTransactionManager") PlatformTransactionManager transactionManager,
			JsonMapper json) {
		this.organizations = organizations;
		this.subscriptions = subscriptions;
		this.auditLog = auditLog;
		this.transaction = new TransactionTemplate(transactionManager);
		this.json = json;
	}

	@Override
	public Optional<Organization> findByCode(String code) {
		return organizations.findByCode(code).map(JpaOrganizationRepository::toDomain);
	}

	@Override
	public boolean existsByCode(String code) {
		return organizations.countByCode(code) > 0;
	}

	@Override
	public boolean existsByEmail(String email) {
		return organizations.countByEmail(email) > 0;
	}

	@Override
	public Result<Organization, Duplicate> register(Registration registration) {
		try {
			return Result.success(transaction.execute(_ -> save(registration)));
		}
		catch (DataIntegrityViolationException e) {
			// MySQL names the violated key: "Duplicate entry 'x' for key 'Organization.uk_organization_code'"
			String detail = String.valueOf(e.getMostSpecificCause().getMessage());
			if (detail.contains("uk_organization_code")) {
				return Result.failure(Duplicate.CODE);
			}
			if (detail.contains("uk_organization_email")) {
				return Result.failure(Duplicate.EMAIL);
			}
			throw e;
		}
	}

	/** The organization, its subscription and the audit entries, in the caller's transaction. */
	private Organization save(Registration registration) {
		String tenantId = registration.tenantId().toString();
		organizations.insert(tenantId, registration.code(), registration.name(), registration.email(),
				registration.database().host(), registration.database().name(), registration.database().username(),
				registration.database().encryptedPassword());
		long organizationId = organizations.findIdByTenantId(tenantId);

		subscriptions.insert(organizationId, registration.plan().name(), registration.plan().maxProjects(),
				registration.plan().maxUsers(), registration.plan().maxStorageMB());
		long subscriptionId = subscriptions.findIdByOrganizationId(organizationId);

		audit(organizationId, "ORGANIZATION_REGISTERED", "Organization", organizationId, registration.ownerUserCode(),
				Map.of("organizationCode", registration.code(), "name", registration.name()));
		audit(organizationId, "SUBSCRIPTION_CREATED", "Subscription", subscriptionId, registration.ownerUserCode(),
				Map.of("plan", registration.plan().name()));
		return new Organization(organizationId, registration.tenantId(), registration.code(), registration.name(),
				registration.email(), true);
	}

	private void audit(long organizationId, String action, String entityType, long entityId, UUID changedBy,
			Map<String, String> newValue) {
		auditLog.insert(organizationId, action, entityType, entityId, changedBy.toString(), json.writeValueAsString(newValue));
	}

	private static Organization toDomain(OrganizationEntity entity) {
		return new Organization(entity.id, entity.tenantId, entity.code, entity.name, entity.email, entity.active);
	}

}

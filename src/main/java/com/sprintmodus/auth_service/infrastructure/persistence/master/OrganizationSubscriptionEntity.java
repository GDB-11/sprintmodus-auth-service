package com.sprintmodus.auth_service.infrastructure.persistence.master;

import java.time.Instant;

import com.sprintmodus.auth_service.domain.model.Plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Persistence model of a row of {@code OrganizationSubscription}, as returned by {@link OrganizationSubscriptionQueries}. */
@Entity
@Table(name = "OrganizationSubscription")
class OrganizationSubscriptionEntity {

	@Id
	@Column(name = "SubscriptionId")
	Long id;

	@Column(name = "OrganizationId")
	Long organizationId;

	@Enumerated(EnumType.STRING)
	@Column(name = "PlanType")
	Plan plan;

	@Column(name = "MaxProjects")
	int maxProjects;

	@Column(name = "MaxUsers")
	int maxUsers;

	@Column(name = "MaxStorageMB")
	int maxStorageMB;

	/** {@code null} = the subscription never expires. */
	@Column(name = "ExpiresAt")
	Instant expiresAt;

	@Column(name = "IsActive")
	boolean active;

}

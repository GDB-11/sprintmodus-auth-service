package com.sprintmodus.auth_service.application.service;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sprintmodus.auth_service.application.dto.AuthenticatedSession;
import com.sprintmodus.auth_service.application.error.MemberError;
import com.sprintmodus.auth_service.application.port.persistence.OrganizationRepository;
import com.sprintmodus.auth_service.application.port.persistence.SubscriptionRepository;
import com.sprintmodus.auth_service.application.port.persistence.TenantUserRepository;
import com.sprintmodus.auth_service.domain.model.OrganizationRole;
import com.sprintmodus.auth_service.domain.model.Plan;
import com.sprintmodus.common_lib.result.Result;

/**
 * Moves the caller's organization to another plan. Only the OWNER may, and the master DB is the one record of the plan:
 * every other user picks the change up at their next login or refresh. There is no payment processor yet, so the change
 * is applied as asked; going to a plan that allows fewer users than the organization has active is refused. The caller
 * gets a fresh session so their own token carries the new limits at once.
 */
@Service
public class ChangePlanUseCase {

	private final MemberCallerResolver callers;

	private final SubscriptionRepository subscriptions;

	private final TenantUserRepository users;

	private final OrganizationRepository organizations;

	private final SessionIssuer sessions;

	public ChangePlanUseCase(MemberCallerResolver callers, SubscriptionRepository subscriptions, TenantUserRepository users,
			OrganizationRepository organizations, SessionIssuer sessions) {
		this.callers = callers;
		this.subscriptions = subscriptions;
		this.users = users;
		this.organizations = organizations;
		this.sessions = sessions;
	}

	public Result<AuthenticatedSession, MemberError> execute(String token, String rawPlan) {
		return callers.resolveMember(token).flatMap(caller -> change(caller, rawPlan));
	}

	private Result<AuthenticatedSession, MemberError> change(MemberCallerResolver.Caller caller, String rawPlan) {
		if (caller.user().role() != OrganizationRole.OWNER) {
			return Result.failure(new MemberError.NotOwner());
		}
		Plan plan = parse(rawPlan);
		if (plan == null) {
			return Result.failure(new MemberError.InvalidUserData("plan", "Choose a valid plan."));
		}
		UUID tenantId = caller.organization().tenantId();
		long activeUsers = users.list(tenantId, 0, 1).activeCount();
		if (activeUsers > plan.maxUsers()) {
			return Result.failure(new MemberError.PlanLimitExceeded((int) activeUsers, plan.maxUsers()));
		}
		Plan previous = caller.subscription().plan();
		var updated = plan == previous && caller.subscription().expiresAt() == null ? caller.subscription()
				: subscriptions.changePlan(caller.organization().id(), plan);
		if (plan != previous) {
			organizations.recordPlanChanged(caller.organization().id(), caller.user().userCode(), previous, plan);
		}
		return Result.success(sessions.issue(caller.organization(), updated, caller.user()));
	}

	private static Plan parse(String raw) {
		if (raw == null) {
			return null;
		}
		try {
			return Plan.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException _) {
			return null;
		}
	}

}

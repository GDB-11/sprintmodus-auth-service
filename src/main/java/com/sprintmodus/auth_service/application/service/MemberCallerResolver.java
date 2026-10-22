package com.sprintmodus.auth_service.application.service;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.sprintmodus.auth_service.application.error.MemberError;
import com.sprintmodus.auth_service.application.port.external.TokenProvider;
import com.sprintmodus.auth_service.application.port.persistence.OrganizationRepository;
import com.sprintmodus.auth_service.application.port.persistence.SubscriptionRepository;
import com.sprintmodus.auth_service.application.port.persistence.TenantUserRepository;
import com.sprintmodus.auth_service.domain.model.Organization;
import com.sprintmodus.auth_service.domain.model.OrganizationRole;
import com.sprintmodus.auth_service.domain.model.OrganizationSubscription;
import com.sprintmodus.auth_service.domain.model.TenantUser;
import com.sprintmodus.common_lib.result.Result;

/**
 * Works out who is managing members, from the bearer token and then from the databases: the role and the limit used
 * afterwards are the current ones (tenant user row, master subscription), never the token's claims, which can be stale
 * until the next refresh.
 */
@Component
public class MemberCallerResolver {

	/** The verified caller, their organization and its subscription. */
	public record Caller(Organization organization, OrganizationSubscription subscription, TenantUser user) {

		public boolean canManageMembers() {
			return user.role() == OrganizationRole.OWNER || user.role() == OrganizationRole.ADMIN;
		}

	}

	private final TokenProvider tokens;

	private final OrganizationRepository organizations;

	private final SubscriptionRepository subscriptions;

	private final TenantUserRepository users;

	private final Clock clock;

	public MemberCallerResolver(TokenProvider tokens, OrganizationRepository organizations, SubscriptionRepository subscriptions,
			TenantUserRepository users, Clock clock) {
		this.tokens = tokens;
		this.organizations = organizations;
		this.subscriptions = subscriptions;
		this.users = users;
		this.clock = clock;
	}

	/** The caller if the token is valid and they may manage members; otherwise why not. */
	public Result<Caller, MemberError> resolveManager(String token) {
		return resolveMember(token)
				.flatMap(caller -> caller.canManageMembers() ? Result.success(caller) : Result.failure(new MemberError.Forbidden()));
	}

	/** The caller if the token is valid and they are an active user of an active organization, whatever their role. */
	public Result<Caller, MemberError> resolveMember(String token) {
		var verified = tokens.verify(token);
		if (verified.isFailure()) {
			return Result.failure(new MemberError.Unauthenticated());
		}
		var claims = verified.getValue().claims();
		var organization = organizations.findByTenantId(claims.tenantId()).filter(Organization::active);
		if (organization.isEmpty()) {
			return Result.failure(new MemberError.Unauthenticated());
		}
		var subscription = subscriptions.findByOrganizationId(organization.get().id());
		if (subscription.isEmpty() || !subscription.get().isUsableAt(clock.instant())) {
			return Result.failure(new MemberError.SubscriptionInactive());
		}
		var user = users.findByCode(organization.get().tenantId(), claims.userCode()).filter(TenantUser::active);
		if (user.isEmpty()) {
			return Result.failure(new MemberError.Unauthenticated());
		}
		return Result.success(new Caller(organization.get(), subscription.get(), user.get()));
	}

}

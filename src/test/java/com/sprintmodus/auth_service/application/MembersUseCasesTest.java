package com.sprintmodus.auth_service.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sprintmodus.auth_service.application.dto.ChangePasswordCommand;
import com.sprintmodus.auth_service.application.dto.CreateUserCommand;
import com.sprintmodus.auth_service.application.dto.ResetPasswordCommand;
import com.sprintmodus.auth_service.application.dto.SetUserActiveCommand;
import com.sprintmodus.auth_service.application.service.SetUserActiveUseCase;
import com.sprintmodus.auth_service.application.dto.TokenClaims;
import com.sprintmodus.auth_service.application.error.MemberError;
import com.sprintmodus.auth_service.application.error.TokenError;
import com.sprintmodus.auth_service.application.service.ChangePasswordUseCase;
import com.sprintmodus.auth_service.application.service.ChangePlanUseCase;
import com.sprintmodus.auth_service.application.service.SessionIssuer;
import com.sprintmodus.auth_service.application.service.CreateUserUseCase;
import com.sprintmodus.auth_service.application.service.ListUsersUseCase;
import com.sprintmodus.auth_service.application.service.MemberCallerResolver;
import com.sprintmodus.auth_service.application.service.ResetPasswordUseCase;
import com.sprintmodus.auth_service.domain.model.Organization;
import com.sprintmodus.auth_service.domain.model.OrganizationRole;
import com.sprintmodus.auth_service.domain.model.Plan;
import com.sprintmodus.auth_service.domain.model.TenantUser;
import com.sprintmodus.auth_service.support.Fakes;
import com.sprintmodus.common_lib.result.Result;

class MembersUseCasesTest {

	private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");

	private final Fakes.Organizations organizations = new Fakes.Organizations();

	private final Fakes.Subscriptions subscriptions = new Fakes.Subscriptions();

	private final Fakes.TenantUsers users = new Fakes.TenantUsers();

	private final Fakes.Tokens tokens = new Fakes.Tokens();

	private final UUID tenantId = UUID.randomUUID();

	private CreateUserUseCase create;

	private ListUsersUseCase list;

	private ResetPasswordUseCase reset;

	private SetUserActiveUseCase setActive;

	private ChangePasswordUseCase change;

	private ChangePlanUseCase plans;

	private Organization acme;

	@BeforeEach
	void setUp() {
		var callers = new MemberCallerResolver(tokens, organizations, subscriptions, users,
				Clock.fixed(NOW, ZoneOffset.UTC));
		create = new CreateUserUseCase(callers, users, organizations, new Fakes.Hasher());
		list = new ListUsersUseCase(callers, users);
		reset = new ResetPasswordUseCase(callers, users, organizations, new Fakes.Hasher());
		setActive = new SetUserActiveUseCase(callers, users, organizations);
		change = new ChangePasswordUseCase(callers, users, organizations, new Fakes.Hasher());
		plans = new ChangePlanUseCase(callers, subscriptions, users, organizations, new SessionIssuer(tokens));
		acme = organizations.add("acme", tenantId, true);
		subscriptions.add(acme.id(), Plan.FREE, null, true);
	}

	private String tokenOf(TenantUser user) {
		String token = "t-" + user.userCode();
		tokens.register(token, Result.success(new TokenClaims.Verified(new TokenClaims(user.userCode(), user.email(),
				tenantId, "acme", user.role(), Plan.FREE, 1, 999, 100), NOW.plusSeconds(60))));
		return token;
	}

	private CreateUserCommand add(String token, String email, String role) {
		return new CreateUserCommand(token, email, "Nuevo Usuario", role, "long-enough-pw");
	}

	@Test
	void ownerAddsAMemberAndItIsAuditedAndHashed() {
		var owner = users.add(tenantId, "owner@acme.io", "h", OrganizationRole.OWNER, true);

		var result = create.execute(add(tokenOf(owner), "  New@Acme.io ", "member"));

		assertThat(result.isSuccess()).isTrue();
		assertThat(result.getValue().email()).isEqualTo("new@acme.io");
		assertThat(result.getValue().role()).isEqualTo(OrganizationRole.MEMBER);
		assertThat(users.created).singleElement().satisfies(created -> assertThat(created.passwordHash()).isEqualTo("hashed:long-enough-pw"));
		assertThat(organizations.userAdditions).hasSize(1);
	}

	@Test
	void roleMatrix() {
		var owner = users.add(tenantId, "owner@acme.io", "h", OrganizationRole.OWNER, true);
		var admin = users.add(tenantId, "admin@acme.io", "h", OrganizationRole.ADMIN, true);
		subscriptions.add(acme.id(), Plan.ENTERPRISE, null, true);

		assertThat(create.execute(add(tokenOf(owner), "a1@acme.io", "ADMIN")).isSuccess()).isTrue();
		assertThat(create.execute(add(tokenOf(admin), "m1@acme.io", "MEMBER")).isSuccess()).isTrue();
		assertThat(create.execute(add(tokenOf(admin), "a2@acme.io", "ADMIN")).getError())
				.isInstanceOf(MemberError.RoleNotAllowed.class);
		assertThat(create.execute(add(tokenOf(admin), "o2@acme.io", "OWNER")).getError())
				.isInstanceOf(MemberError.RoleNotAllowed.class);
		assertThat(create.execute(add(tokenOf(owner), "o3@acme.io", "OWNER")).getError())
				.isInstanceOf(MemberError.RoleNotAllowed.class);
	}

	@Test
	void aMemberCannotAddOrList() {
		var member = users.add(tenantId, "m@acme.io", "h", OrganizationRole.MEMBER, true);

		assertThat(create.execute(add(tokenOf(member), "x@acme.io", "MEMBER")).getError())
				.isInstanceOf(MemberError.Forbidden.class);
		assertThat(list.execute(tokenOf(member), 0, 20).getError()).isInstanceOf(MemberError.Forbidden.class);
		assertThat(users.created).isEmpty();
	}

	@Test
	void badInputIsRefusedBeforeAnythingIsWritten() {
		var owner = users.add(tenantId, "owner@acme.io", "h", OrganizationRole.OWNER, true);
		String token = tokenOf(owner);

		assertThat(field(create.execute(add(token, "not-an-email", "MEMBER")))).isEqualTo("email");
		assertThat(field(create.execute(new CreateUserCommand(token, "x@acme.io", " ", "MEMBER", "long-enough-pw"))))
				.isEqualTo("fullName");
		assertThat(field(create.execute(new CreateUserCommand(token, "x@acme.io", "X", "MEMBER", "short"))))
				.isEqualTo("password");
		assertThat(field(create.execute(add(token, "x@acme.io", "BOSS")))).isEqualTo("role");
		assertThat(field(create.execute(add(token, "x@acme.io", null)))).isEqualTo("role");
		assertThat(users.created).isEmpty();
	}

	private static String field(Result<?, MemberError> result) {
		return ((MemberError.InvalidUserData) result.getError()).field();
	}

	@Test
	void duplicateEmailIsTakenEvenForADeactivatedUser() {
		var owner = users.add(tenantId, "owner@acme.io", "h", OrganizationRole.OWNER, true);
		users.add(tenantId, "gone@acme.io", "h", OrganizationRole.MEMBER, false);

		assertThat(create.execute(add(tokenOf(owner), "gone@acme.io", "MEMBER")).getError())
				.isInstanceOf(MemberError.EmailTaken.class);
	}

	@Test
	void stopsExactlyAtTheLimitReadFromTheSubscriptionNotTheToken() {
		// FREE allows Plan.FREE.maxUsers(); the token above claims 999
		int max = Plan.FREE.maxUsers();
		var owner = users.add(tenantId, "owner@acme.io", "h", OrganizationRole.OWNER, true);
		String token = tokenOf(owner);
		for (int i = 1; i < max; i++) {
			assertThat(create.execute(add(token, "u" + i + "@acme.io", "MEMBER")).isSuccess()).isTrue();
		}

		var refused = create.execute(add(token, "last@acme.io", "MEMBER"));

		assertThat(refused.getError()).isEqualTo(new MemberError.UserLimitReached(max));
		assertThat(organizations.userAdditions).hasSize(max - 1);
	}

	@Test
	void aDeactivatedUserFreesASeat() {
		int max = Plan.FREE.maxUsers();
		var owner = users.add(tenantId, "owner@acme.io", "h", OrganizationRole.OWNER, true);
		for (int i = 1; i < max; i++) {
			users.add(tenantId, "u" + i + "@acme.io", "h", OrganizationRole.MEMBER, i == 1 ? false : true);
		}

		assertThat(create.execute(add(tokenOf(owner), "new@acme.io", "MEMBER")).isSuccess()).isTrue();
	}

	@Test
	void invalidTokensAndInactiveCallersAreUnauthenticated() {
		var gone = users.add(tenantId, "gone@acme.io", "h", OrganizationRole.OWNER, false);
		tokens.register("expired", Result.failure(new TokenError.ExpiredToken()));

		assertThat(create.execute(add("expired", "x@acme.io", "MEMBER")).getError()).isInstanceOf(MemberError.Unauthenticated.class);
		assertThat(create.execute(add(null, "x@acme.io", "MEMBER")).getError()).isInstanceOf(MemberError.Unauthenticated.class);
		assertThat(create.execute(add(tokenOf(gone), "x@acme.io", "MEMBER")).getError()).isInstanceOf(MemberError.Unauthenticated.class);
	}

	@Test
	void anInactiveSubscriptionIsRefused() {
		var owner = users.add(tenantId, "owner@acme.io", "h", OrganizationRole.OWNER, true);
		subscriptions.add(acme.id(), Plan.FREE, null, false);

		assertThat(create.execute(add(tokenOf(owner), "x@acme.io", "MEMBER")).getError())
				.isInstanceOf(MemberError.SubscriptionInactive.class);
	}

	@Test
	void aTokenForAnotherTenantNeverReachesThisOne() {
		var otherTenant = UUID.randomUUID();
		var other = organizations.add("other", otherTenant, true);
		subscriptions.add(other.id(), Plan.FREE, null, true);
		var otherOwner = users.add(otherTenant, "o@other.io", "h", OrganizationRole.OWNER, true);
		users.add(tenantId, "owner@acme.io", "h", OrganizationRole.OWNER, true);
		String token = "other-token";
		tokens.register(token, Result.success(new TokenClaims.Verified(new TokenClaims(otherOwner.userCode(), "o@other.io",
				otherTenant, "other", OrganizationRole.OWNER, Plan.FREE, 1, 5, 100), NOW.plusSeconds(60))));

		var page = list.execute(token, 0, 20).getValue();

		assertThat(page.items()).extracting(member -> member.email()).containsExactly("o@other.io");
		assertThat(page.maxUsers()).isEqualTo(Plan.FREE.maxUsers());
	}

	@Test
	void listReportsSeatUsageAndClampsPaging() {
		var owner = users.add(tenantId, "owner@acme.io", "h", OrganizationRole.OWNER, true);
		users.add(tenantId, "off@acme.io", "h", OrganizationRole.MEMBER, false);

		var page = list.execute(tokenOf(owner), -3, 5000).getValue();

		assertThat(page.page()).isZero();
		assertThat(page.size()).isEqualTo(100);
		assertThat(page.total()).isEqualTo(2);
		assertThat(page.activeCount()).isEqualTo(1);
	}


	@Test
	void ownerResetsAMembersPasswordAndItIsAudited() {
		var owner = users.add(tenantId, "owner@acme.io", "h", OrganizationRole.OWNER, true);
		var member = users.add(tenantId, "m@acme.io", "old", OrganizationRole.MEMBER, true);

		var result = reset.execute(new ResetPasswordCommand(tokenOf(owner), member.userCode().toString(), "brand-new-pw"));

		assertThat(result.isSuccess()).isTrue();
		assertThat(users.findByCode(tenantId, member.userCode()).orElseThrow().passwordHash()).isEqualTo("hashed:brand-new-pw");
		assertThat(organizations.passwordChanges).hasSize(1);
	}

	@Test
	void anAdminCannotResetAnAdminOrTheOwner() {
		var owner = users.add(tenantId, "owner@acme.io", "h", OrganizationRole.OWNER, true);
		var admin = users.add(tenantId, "a@acme.io", "h", OrganizationRole.ADMIN, true);
		var other = users.add(tenantId, "b@acme.io", "h", OrganizationRole.ADMIN, true);

		for (var target : new TenantUser[] { owner, other, admin }) {
			var result = reset.execute(new ResetPasswordCommand(tokenOf(admin), target.userCode().toString(), "brand-new-pw"));
			assertThat(result.getError()).isInstanceOf(MemberError.RoleNotAllowed.class);
		}
		assertThat(organizations.passwordChanges).isEmpty();
	}

	@Test
	void aMemberCannotResetAndAnUnknownUserIsNotFound() {
		var owner = users.add(tenantId, "owner@acme.io", "h", OrganizationRole.OWNER, true);
		var member = users.add(tenantId, "m@acme.io", "h", OrganizationRole.MEMBER, true);

		assertThat(reset.execute(new ResetPasswordCommand(tokenOf(member), member.userCode().toString(), "brand-new-pw")).getError())
				.isInstanceOf(MemberError.Forbidden.class);
		assertThat(reset.execute(new ResetPasswordCommand(tokenOf(owner), UUID.randomUUID().toString(), "brand-new-pw")).getError())
				.isInstanceOf(MemberError.UserNotFound.class);
		assertThat(reset.execute(new ResetPasswordCommand(tokenOf(owner), "not-a-uuid", "brand-new-pw")).getError())
				.isInstanceOf(MemberError.UserNotFound.class);
	}

	@Test
	void ownerDeactivatesAndReactivatesAMemberAndItIsAudited() {
		var owner = users.add(tenantId, "owner@acme.io", "h", OrganizationRole.OWNER, true);
		var member = users.add(tenantId, "m@acme.io", "h", OrganizationRole.MEMBER, true);

		assertThat(setActive.execute(new SetUserActiveCommand(tokenOf(owner), member.userCode().toString(), false)).isSuccess()).isTrue();
		assertThat(users.findByCode(tenantId, member.userCode()).orElseThrow().active()).isFalse();
		assertThat(setActive.execute(new SetUserActiveCommand(tokenOf(owner), member.userCode().toString(), true)).isSuccess()).isTrue();
		assertThat(users.findByCode(tenantId, member.userCode()).orElseThrow().active()).isTrue();
		assertThat(organizations.activeChanges).containsExactly(member.userCode() + ":false", member.userCode() + ":true");
	}

	@Test
	void nobodyDeactivatesTheOwnerThemselvesOrAPeerAdmin() {
		var owner = users.add(tenantId, "owner@acme.io", "h", OrganizationRole.OWNER, true);
		var admin = users.add(tenantId, "a@acme.io", "h", OrganizationRole.ADMIN, true);
		var other = users.add(tenantId, "b@acme.io", "h", OrganizationRole.ADMIN, true);

		for (var target : new TenantUser[] { owner, other, admin }) {
			assertThat(setActive.execute(new SetUserActiveCommand(tokenOf(admin), target.userCode().toString(), false)).getError())
					.isInstanceOf(MemberError.RoleNotAllowed.class);
		}
		assertThat(setActive.execute(new SetUserActiveCommand(tokenOf(owner), owner.userCode().toString(), false)).getError())
				.isInstanceOf(MemberError.RoleNotAllowed.class);
		assertThat(organizations.activeChanges).isEmpty();
	}

	@Test
	void reactivatingCountsAgainstThePlanLimit() {
		var owner = users.add(tenantId, "owner@acme.io", "h", OrganizationRole.OWNER, true);
		var member = users.add(tenantId, "m@acme.io", "h", OrganizationRole.MEMBER, false);
		subscriptions.add(acme.id(), Plan.FREE, null, true);
		int max = subscriptions.findByOrganizationId(acme.id()).orElseThrow().maxUsers();
		for (int i = 1; i < max; i++) {
			users.add(tenantId, "x" + i + "@acme.io", "h", OrganizationRole.MEMBER, true);
		}

		var result = setActive.execute(new SetUserActiveCommand(tokenOf(owner), member.userCode().toString(), true));

		assertThat(result.getError()).isInstanceOf(MemberError.UserLimitReached.class);
		assertThat(users.findByCode(tenantId, member.userCode()).orElseThrow().active()).isFalse();
	}

	@Test
	void resetRejectsAWeakPassword() {
		var owner = users.add(tenantId, "owner@acme.io", "h", OrganizationRole.OWNER, true);
		var member = users.add(tenantId, "m@acme.io", "h", OrganizationRole.MEMBER, true);

		var result = reset.execute(new ResetPasswordCommand(tokenOf(owner), member.userCode().toString(), "short"));

		assertThat(result.getError()).isInstanceOf(MemberError.InvalidUserData.class);
	}

	@Test
	void anyRoleChangesTheirOwnPasswordWithTheCurrentOne() {
		var member = users.add(tenantId, "m@acme.io", "hashed:old-password", OrganizationRole.MEMBER, true);

		var result = change.execute(new ChangePasswordCommand(tokenOf(member), "old-password", "brand-new-pw"));

		assertThat(result.isSuccess()).isTrue();
		assertThat(users.findByCode(tenantId, member.userCode()).orElseThrow().passwordHash()).isEqualTo("hashed:brand-new-pw");
		assertThat(organizations.passwordChanges).hasSize(1);
	}

	@Test
	void changeNeedsTheRightCurrentPasswordAndAValidNewOne() {
		var member = users.add(tenantId, "m@acme.io", "hashed:old-password", OrganizationRole.MEMBER, true);

		assertThat(change.execute(new ChangePasswordCommand(tokenOf(member), "wrong", "brand-new-pw")).getError())
				.isInstanceOf(MemberError.WrongCurrentPassword.class);
		assertThat(change.execute(new ChangePasswordCommand(tokenOf(member), "old-password", "short")).getError())
				.isInstanceOf(MemberError.InvalidUserData.class);
		assertThat(change.execute(new ChangePasswordCommand(null, "old-password", "brand-new-pw")).getError())
				.isInstanceOf(MemberError.Unauthenticated.class);
		assertThat(organizations.passwordChanges).isEmpty();
	}


	@Test
	void ownerChangesThePlanAndGetsASessionWithTheNewLimits() {
		var owner = users.add(tenantId, "owner@acme.io", "h", OrganizationRole.OWNER, true);

		var result = plans.execute(tokenOf(owner), "pro");

		assertThat(result.isSuccess()).isTrue();
		assertThat(result.getValue().subscription().plan()).isEqualTo(Plan.PRO);
		assertThat(tokens.lastIssued.maxUsers()).isEqualTo(Plan.PRO.maxUsers());
		assertThat(subscriptions.findByOrganizationId(acme.id()).orElseThrow().plan()).isEqualTo(Plan.PRO);
		assertThat(organizations.planChanges).containsExactly(acme.id() + ":FREE>PRO");
	}

	@Test
	void onlyTheOwnerChangesThePlanAndAnUnknownPlanIsRefused() {
		var owner = users.add(tenantId, "owner@acme.io", "h", OrganizationRole.OWNER, true);
		var admin = users.add(tenantId, "admin@acme.io", "h", OrganizationRole.ADMIN, true);
		var member = users.add(tenantId, "m@acme.io", "h", OrganizationRole.MEMBER, true);

		assertThat(plans.execute(tokenOf(admin), "PRO").getError()).isInstanceOf(MemberError.NotOwner.class);
		assertThat(plans.execute(tokenOf(member), "PRO").getError()).isInstanceOf(MemberError.NotOwner.class);
		assertThat(plans.execute(tokenOf(owner), "GOLD").getError()).isInstanceOf(MemberError.InvalidUserData.class);
		assertThat(plans.execute(tokenOf(owner), null).getError()).isInstanceOf(MemberError.InvalidUserData.class);
		assertThat(subscriptions.findByOrganizationId(acme.id()).orElseThrow().plan()).isEqualTo(Plan.FREE);
		assertThat(organizations.planChanges).isEmpty();
	}

	@Test
	void aDowngradeBelowTheActiveUsersIsRefused() {
		var owner = users.add(tenantId, "owner@acme.io", "h", OrganizationRole.OWNER, true);
		subscriptions.add(acme.id(), Plan.PRO, null, true);
		for (int i = 0; i < Plan.FREE.maxUsers(); i++) {
			users.add(tenantId, "m" + i + "@acme.io", "h", OrganizationRole.MEMBER, true);
		}

		var error = plans.execute(tokenOf(owner), "FREE").getError();

		assertThat(error).isInstanceOf(MemberError.PlanLimitExceeded.class);
		assertThat(subscriptions.findByOrganizationId(acme.id()).orElseThrow().plan()).isEqualTo(Plan.PRO);
	}

}

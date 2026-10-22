package com.sprintmodus.auth_service.application.error;

import com.sprintmodus.common_lib.result.ApplicationError;

/** Why the members of an organization could not be listed or a member could not be added. */
public sealed interface MemberError extends ApplicationError {

	/** Missing, malformed or expired token, or the caller no longer exists or is deactivated. */
	record Unauthenticated() implements MemberError {

		@Override
		public String code() {
			return "INVALID_TOKEN";
		}

		@Override
		public String message() {
			return "Invalid or expired token.";
		}

	}

	record SubscriptionInactive() implements MemberError {

		@Override
		public String code() {
			return "SUBSCRIPTION_INACTIVE";
		}

		@Override
		public String message() {
			return "The organization's subscription is not active.";
		}

	}

	/** The caller is not an OWNER or ADMIN. */
	record Forbidden() implements MemberError {

		@Override
		public String code() {
			return "FORBIDDEN";
		}

		@Override
		public String message() {
			return "Only an owner or an administrator can manage the organization's members.";
		}

	}

	/** Only the organization's OWNER may change its subscription. */
	record NotOwner() implements MemberError {

		@Override
		public String code() {
			return "OWNER_ONLY";
		}

		@Override
		public String message() {
			return "Only the owner can change the organization's subscription.";
		}

	}

	/** The plan asked for allows fewer active users than the organization has now. */
	record PlanLimitExceeded(int activeUsers, int maxUsers) implements MemberError {

		@Override
		public String code() {
			return "PLAN_LIMIT_EXCEEDED";
		}

		@Override
		public String message() {
			return "The organization has " + activeUsers + " active users and the plan allows " + maxUsers
					+ ". Deactivate users or keep the current plan.";
		}

	}

	/** The caller may manage members, but not with this role (an ADMIN asking for ADMIN, anyone asking for OWNER). */
	record RoleNotAllowed() implements MemberError {

		@Override
		public String code() {
			return "ROLE_NOT_ALLOWED";
		}

		@Override
		public String message() {
			return "You are not allowed to grant this role.";
		}

	}

	/** A field of the request is missing or malformed; {@code message} says what to fix. */
	record InvalidUserData(String field, String message) implements MemberError {

		@Override
		public String code() {
			return "INVALID_USER_DATA";
		}

	}

	/** The user to act on is not in this organization (or was deleted). */
	record UserNotFound() implements MemberError {

		@Override
		public String code() {
			return "USER_NOT_FOUND";
		}

		@Override
		public String message() {
			return "The user does not exist in the organization.";
		}

	}

	/** The current password given to change one's own password is wrong. */
	record WrongCurrentPassword() implements MemberError {

		@Override
		public String code() {
			return "WRONG_CURRENT_PASSWORD";
		}

		@Override
		public String message() {
			return "The current password is not correct.";
		}

	}

	record EmailTaken() implements MemberError {

		@Override
		public String code() {
			return "EMAIL_TAKEN";
		}

		@Override
		public String message() {
			return "A user with this email already exists in the organization.";
		}

	}

	record UserLimitReached(int maxUsers) implements MemberError {

		@Override
		public String code() {
			return "USER_LIMIT_REACHED";
		}

		@Override
		public String message() {
			return "The plan allows " + maxUsers + " active users and the organization already has them.";
		}

	}

}

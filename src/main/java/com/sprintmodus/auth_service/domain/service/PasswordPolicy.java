package com.sprintmodus.auth_service.domain.service;

import java.nio.charset.StandardCharsets;

import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.common_lib.result.Unit;

/** Rules for a new password. The upper bound is BCrypt's: it ignores everything past 72 bytes. */
public final class PasswordPolicy {

	public static final int MIN_LENGTH = 8;

	public static final int MAX_BYTES = 72;

	private PasswordPolicy() {
	}

	/** Returns the reason the password is rejected, safe to show to the user. */
	public static Result<Unit, String> validate(String password) {
		if (password == null || password.length() < MIN_LENGTH) {
			return Result.failure("The password must have at least " + MIN_LENGTH + " characters.");
		}
		if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
			return Result.failure("The password is too long.");
		}
		return Result.success(Unit.VALUE);
	}

}

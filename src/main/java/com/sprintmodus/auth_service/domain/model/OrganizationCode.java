package com.sprintmodus.auth_service.domain.model;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.sprintmodus.common_lib.result.Result;

/**
 * The short code an organization's owner picks and members type at login, e.g. {@code acme}. Always stored lowercase:
 * 3 to 30 letters, digits or hyphens, with no hyphen at either end. The database enforces the same shape.
 */
public record OrganizationCode(String value) {

	public enum Problem {

		INVALID_FORMAT,
		RESERVED

	}

	private static final Pattern SHAPE = Pattern.compile("[a-z0-9][a-z0-9-]{1,28}[a-z0-9]");

	/** Codes that would clash with product names, routes or infrastructure. */
	private static final Set<String> RESERVED = Set.of("admin", "administrator", "api", "app", "assets", "auth", "billing",
			"blog", "dashboard", "docs", "eureka", "gateway", "help", "login", "logout", "mail", "register", "root",
			"signin", "signup", "sprintmodus", "static", "status", "support", "system", "www", "ws");

	/** Lowercases and trims, without validating. Used for lookups where a bad code just means "not found". */
	public static String normalize(String raw) {
		return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
	}

	public static Result<OrganizationCode, Problem> parse(String raw) {
		String code = normalize(raw);
		if (!SHAPE.matcher(code).matches()) {
			return Result.failure(Problem.INVALID_FORMAT);
		}
		if (RESERVED.contains(code)) {
			return Result.failure(Problem.RESERVED);
		}
		return Result.success(new OrganizationCode(code));
	}

}

package com.sprintmodus.auth_service.domain.service;

import java.util.Locale;
import java.util.regex.Pattern;

/** Normalization and a sanity check for email addresses. Real ownership is proven by mail, not by a regex. */
public final class EmailAddress {

	public static final int MAX_LENGTH = 255;

	private static final Pattern SHAPE = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

	private EmailAddress() {
	}

	public static String normalize(String raw) {
		return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
	}

	public static boolean isValid(String normalized) {
		return normalized.length() <= MAX_LENGTH && SHAPE.matcher(normalized).matches();
	}

}

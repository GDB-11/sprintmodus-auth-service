package com.sprintmodus.auth_service.application.port.external;

public interface PasswordHasher {

	String hash(String rawPassword);

	boolean matches(String rawPassword, String hash);

}

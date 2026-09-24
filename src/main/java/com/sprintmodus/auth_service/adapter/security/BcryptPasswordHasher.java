package com.sprintmodus.auth_service.adapter.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.sprintmodus.auth_service.application.port.external.PasswordHasher;

@Component
class BcryptPasswordHasher implements PasswordHasher {

	private final PasswordEncoder encoder;

	BcryptPasswordHasher(PasswordEncoder encoder) {
		this.encoder = encoder;
	}

	@Override
	public String hash(String rawPassword) {
		return encoder.encode(rawPassword);
	}

	@Override
	public boolean matches(String rawPassword, String hash) {
		return encoder.matches(rawPassword, hash);
	}

}

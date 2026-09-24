package com.sprintmodus.auth_service.application.service;

import org.springframework.stereotype.Service;

import com.sprintmodus.auth_service.application.dto.TokenClaims;
import com.sprintmodus.auth_service.application.error.TokenError;
import com.sprintmodus.auth_service.application.port.external.TokenProvider;
import com.sprintmodus.common_lib.result.Result;

/** Checks a token's signature, issuer and expiry. It does not hit any database. */
@Service
public class ValidateTokenUseCase {

	private final TokenProvider tokens;

	public ValidateTokenUseCase(TokenProvider tokens) {
		this.tokens = tokens;
	}

	public Result<TokenClaims.Verified, TokenError> execute(String token) {
		return tokens.verify(token);
	}

}

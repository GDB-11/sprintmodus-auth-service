package com.sprintmodus.auth_service.application.port.external;

import com.sprintmodus.auth_service.application.dto.TokenClaims;
import com.sprintmodus.auth_service.application.error.TokenError;
import com.sprintmodus.common_lib.result.Result;

public interface TokenProvider {

	/** Issues a signed token that expires after the configured lifetime. */
	String generate(TokenClaims claims);

	/** Checks signature, issuer and expiry, and reads the claims. Never throws for a bad token. */
	Result<TokenClaims.Verified, TokenError> verify(String token);

}

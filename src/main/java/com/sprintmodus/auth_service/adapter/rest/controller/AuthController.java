package com.sprintmodus.auth_service.adapter.rest.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sprintmodus.auth_service.adapter.rest.dto.LoginRequest;
import com.sprintmodus.auth_service.adapter.rest.dto.OrganizationCodeAvailabilityResponse;
import com.sprintmodus.auth_service.adapter.rest.dto.RegisterOrganizationRequest;
import com.sprintmodus.auth_service.adapter.rest.dto.RegisterOrganizationResponse;
import com.sprintmodus.auth_service.adapter.rest.dto.SessionResponse;
import com.sprintmodus.auth_service.adapter.rest.dto.ValidateTokenResponse;
import com.sprintmodus.auth_service.application.dto.LoginCommand;
import com.sprintmodus.auth_service.application.dto.RegisterOrganizationCommand;
import com.sprintmodus.auth_service.application.service.CheckOrganizationCodeUseCase;
import com.sprintmodus.auth_service.application.service.LoginUseCase;
import com.sprintmodus.auth_service.application.service.RefreshTokenUseCase;
import com.sprintmodus.auth_service.application.service.RegisterOrganizationUseCase;
import com.sprintmodus.auth_service.application.service.ValidateTokenUseCase;

/** Maps HTTP to use cases and their {@code Result}s back to HTTP. It holds no business logic. */
@RestController
@RequestMapping("/auth")
class AuthController {

	private static final String BEARER_PREFIX = "Bearer ";

	private final LoginUseCase login;

	private final RegisterOrganizationUseCase registerOrganization;

	private final CheckOrganizationCodeUseCase checkOrganizationCode;

	private final RefreshTokenUseCase refreshToken;

	private final ValidateTokenUseCase validateToken;

	AuthController(LoginUseCase login, RegisterOrganizationUseCase registerOrganization,
			CheckOrganizationCodeUseCase checkOrganizationCode, RefreshTokenUseCase refreshToken,
			ValidateTokenUseCase validateToken) {
		this.login = login;
		this.registerOrganization = registerOrganization;
		this.checkOrganizationCode = checkOrganizationCode;
		this.refreshToken = refreshToken;
		this.validateToken = validateToken;
	}

	@PostMapping("/login")
	ResponseEntity<?> login(@RequestBody LoginRequest request) {
		return login.execute(new LoginCommand(request.organizationCode(), request.email(), request.password()))
				.fold(session -> ResponseEntity.ok(SessionResponse.from(session)), ErrorMapper::toResponse);
	}

	@PostMapping("/register-organization")
	ResponseEntity<?> registerOrganization(@RequestBody RegisterOrganizationRequest request) {
		return registerOrganization.execute(new RegisterOrganizationCommand(request.organizationName(),
				request.organizationCode(), request.fullName(), request.email(), request.password()))
				.fold(registered -> ResponseEntity.status(HttpStatus.CREATED)
						.body(RegisterOrganizationResponse.from(registered)), ErrorMapper::toResponse);
	}

	@GetMapping("/organization-code-available")
	OrganizationCodeAvailabilityResponse organizationCodeAvailable(@RequestParam("code") String code) {
		return checkOrganizationCode.execute(code).fold(_ -> new OrganizationCodeAvailabilityResponse(true, null),
				error -> new OrganizationCodeAvailabilityResponse(false, error.code()));
	}

	@PostMapping("/refresh-token")
	ResponseEntity<?> refreshToken(@RequestHeader(value = "Authorization", required = false) String authorization) {
		return refreshToken.execute(bearerToken(authorization))
				.fold(session -> ResponseEntity.ok(SessionResponse.from(session)), ErrorMapper::toResponse);
	}

	@GetMapping("/validate-token")
	ResponseEntity<?> validateToken(@RequestHeader(value = "Authorization", required = false) String authorization) {
		return validateToken.execute(bearerToken(authorization))
				.fold(verified -> ResponseEntity.ok(ValidateTokenResponse.from(verified)), ErrorMapper::toResponse);
	}

	/** The token from an {@code Authorization: Bearer <token>} header, or {@code null} if there is none. */
	private static String bearerToken(String authorization) {
		return authorization != null && authorization.startsWith(BEARER_PREFIX)
				? authorization.substring(BEARER_PREFIX.length()).trim() : null;
	}

}

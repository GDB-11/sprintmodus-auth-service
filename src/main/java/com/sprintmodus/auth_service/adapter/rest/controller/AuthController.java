package com.sprintmodus.auth_service.adapter.rest.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sprintmodus.auth_service.adapter.rest.dto.ChangePasswordRequest;
import com.sprintmodus.auth_service.adapter.rest.dto.ChangePlanRequest;
import com.sprintmodus.auth_service.adapter.rest.dto.CreateUserRequest;
import com.sprintmodus.auth_service.adapter.rest.dto.LoginRequest;
import com.sprintmodus.auth_service.adapter.rest.dto.MemberPageResponse;
import com.sprintmodus.auth_service.adapter.rest.dto.MemberResponse;
import com.sprintmodus.auth_service.adapter.rest.dto.OrganizationCodeAvailabilityResponse;
import com.sprintmodus.auth_service.adapter.rest.dto.RegisterOrganizationRequest;
import com.sprintmodus.auth_service.adapter.rest.dto.RegisterOrganizationResponse;
import com.sprintmodus.auth_service.adapter.rest.dto.SessionResponse;
import com.sprintmodus.auth_service.adapter.rest.dto.ResetPasswordRequest;
import com.sprintmodus.auth_service.adapter.rest.dto.SetUserActiveRequest;
import com.sprintmodus.auth_service.adapter.rest.dto.ValidateTokenResponse;
import com.sprintmodus.auth_service.application.dto.ChangePasswordCommand;
import com.sprintmodus.auth_service.application.dto.CreateUserCommand;
import com.sprintmodus.auth_service.application.dto.LoginCommand;
import com.sprintmodus.auth_service.application.dto.RegisterOrganizationCommand;
import com.sprintmodus.auth_service.application.dto.ResetPasswordCommand;
import com.sprintmodus.auth_service.application.dto.SetUserActiveCommand;
import com.sprintmodus.auth_service.application.service.ChangePasswordUseCase;
import com.sprintmodus.auth_service.application.service.ChangePlanUseCase;
import com.sprintmodus.auth_service.application.service.CheckOrganizationCodeUseCase;
import com.sprintmodus.auth_service.application.service.CreateUserUseCase;
import com.sprintmodus.auth_service.application.service.ListUsersUseCase;
import com.sprintmodus.auth_service.application.service.LoginUseCase;
import com.sprintmodus.auth_service.application.service.RefreshTokenUseCase;
import com.sprintmodus.auth_service.application.service.RegisterOrganizationUseCase;
import com.sprintmodus.auth_service.application.service.ResetPasswordUseCase;
import com.sprintmodus.auth_service.application.service.SetUserActiveUseCase;
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

	private final CreateUserUseCase createUser;

	private final ListUsersUseCase listUsers;

	private final ResetPasswordUseCase resetPassword;

	private final SetUserActiveUseCase setUserActive;

	private final ChangePasswordUseCase changePassword;

	private final ChangePlanUseCase changePlan;

	AuthController(LoginUseCase login, RegisterOrganizationUseCase registerOrganization,
			CheckOrganizationCodeUseCase checkOrganizationCode, RefreshTokenUseCase refreshToken,
			ValidateTokenUseCase validateToken, CreateUserUseCase createUser, ListUsersUseCase listUsers,
			ResetPasswordUseCase resetPassword, SetUserActiveUseCase setUserActive, ChangePasswordUseCase changePassword, ChangePlanUseCase changePlan) {
		this.login = login;
		this.registerOrganization = registerOrganization;
		this.checkOrganizationCode = checkOrganizationCode;
		this.refreshToken = refreshToken;
		this.validateToken = validateToken;
		this.createUser = createUser;
		this.listUsers = listUsers;
		this.resetPassword = resetPassword;
		this.setUserActive = setUserActive;
		this.changePassword = changePassword;
		this.changePlan = changePlan;
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

	@PutMapping("/subscription")
	ResponseEntity<?> changePlan(@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestBody ChangePlanRequest request) {
		return changePlan.execute(bearerToken(authorization), request.plan())
				.fold(session -> ResponseEntity.ok(SessionResponse.from(session)), ErrorMapper::toResponse);
	}

	@PostMapping("/users")
	ResponseEntity<?> createUser(@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestBody CreateUserRequest request) {
		return createUser.execute(new CreateUserCommand(bearerToken(authorization), request.email(), request.fullName(),
				request.role(), request.password()))
				.fold(member -> ResponseEntity.status(HttpStatus.CREATED).body(MemberResponse.from(member)),
						ErrorMapper::toResponse);
	}

	@GetMapping("/users")
	ResponseEntity<?> listUsers(@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "20") int size) {
		return listUsers.execute(bearerToken(authorization), page, size)
				.fold(found -> ResponseEntity.ok(MemberPageResponse.from(found)), ErrorMapper::toResponse);
	}

	@PutMapping("/users/{userCode}/password")
	ResponseEntity<?> resetPassword(@RequestHeader(value = "Authorization", required = false) String authorization,
			@PathVariable("userCode") String userCode, @RequestBody ResetPasswordRequest request) {
		return resetPassword.execute(new ResetPasswordCommand(bearerToken(authorization), userCode, request.newPassword()))
				.fold(_ -> ResponseEntity.noContent().build(), ErrorMapper::toResponse);
	}

	@PutMapping("/users/{userCode}/active")
	ResponseEntity<?> setUserActive(@RequestHeader(value = "Authorization", required = false) String authorization,
			@PathVariable("userCode") String userCode, @RequestBody SetUserActiveRequest request) {
		return setUserActive.execute(new SetUserActiveCommand(bearerToken(authorization), userCode, request.active()))
				.fold(_ -> ResponseEntity.noContent().build(), ErrorMapper::toResponse);
	}

	@PostMapping("/change-password")
	ResponseEntity<?> changePassword(@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestBody ChangePasswordRequest request) {
		return changePassword
				.execute(new ChangePasswordCommand(bearerToken(authorization), request.currentPassword(), request.newPassword()))
				.fold(_ -> ResponseEntity.noContent().build(), ErrorMapper::toResponse);
	}

	/** The token from an {@code Authorization: Bearer <token>} header, or {@code null} if there is none. */
	private static String bearerToken(String authorization) {
		return authorization != null && authorization.startsWith(BEARER_PREFIX)
				? authorization.substring(BEARER_PREFIX.length()).trim() : null;
	}

}

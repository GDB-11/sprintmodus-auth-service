package com.sprintmodus.auth_service.application.dto;

/** {@code role} is the raw requested role name, validated by the use case. */
public record CreateUserCommand(String token, String email, String fullName, String role, String password) {
}

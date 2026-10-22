package com.sprintmodus.auth_service.adapter.rest.dto;

public record CreateUserRequest(String email, String fullName, String role, String password) {
}

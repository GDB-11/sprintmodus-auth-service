package com.sprintmodus.auth_service.application.dto;

public record RegisterOrganizationCommand(String organizationName, String organizationCode, String fullName,
		String email, String password) {
}

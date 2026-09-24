package com.sprintmodus.auth_service.adapter.rest.dto;

public record RegisterOrganizationRequest(String organizationName, String organizationCode, String fullName,
		String email, String password) {
}

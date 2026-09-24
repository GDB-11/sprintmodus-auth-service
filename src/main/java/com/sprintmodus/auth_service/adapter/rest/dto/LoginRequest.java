package com.sprintmodus.auth_service.adapter.rest.dto;

public record LoginRequest(String organizationCode, String email, String password) {
}

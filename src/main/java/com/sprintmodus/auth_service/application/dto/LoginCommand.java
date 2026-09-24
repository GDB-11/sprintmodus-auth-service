package com.sprintmodus.auth_service.application.dto;

public record LoginCommand(String organizationCode, String email, String password) {
}

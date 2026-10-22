package com.sprintmodus.auth_service.adapter.rest.dto;

public record ChangePasswordRequest(String currentPassword, String newPassword) {
}

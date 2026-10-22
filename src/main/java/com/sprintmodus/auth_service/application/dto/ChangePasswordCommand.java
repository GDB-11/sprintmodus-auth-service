package com.sprintmodus.auth_service.application.dto;

/** A signed-in user changes their own password. */
public record ChangePasswordCommand(String token, String currentPassword, String newPassword) {
}

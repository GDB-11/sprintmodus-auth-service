package com.sprintmodus.auth_service.application.dto;

/** An OWNER/ADMIN sets a new password for {@code userCode}. */
public record ResetPasswordCommand(String token, String userCode, String newPassword) {
}

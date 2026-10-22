package com.sprintmodus.auth_service.application.dto;

/** An OWNER/ADMIN activates or deactivates {@code userCode}. */
public record SetUserActiveCommand(String token, String userCode, boolean active) {
}

package com.sprintmodus.auth_service.adapter.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** {@code reason} is the error code explaining why a code cannot be used, and absent when it is available. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrganizationCodeAvailabilityResponse(boolean available, String reason) {
}

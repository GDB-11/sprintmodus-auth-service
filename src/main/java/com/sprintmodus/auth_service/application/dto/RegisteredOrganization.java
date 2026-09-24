package com.sprintmodus.auth_service.application.dto;

import com.sprintmodus.auth_service.domain.model.Plan;

public record RegisteredOrganization(String organizationCode, String organizationName, Plan plan) {
}

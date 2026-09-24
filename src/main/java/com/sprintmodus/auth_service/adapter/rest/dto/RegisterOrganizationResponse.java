package com.sprintmodus.auth_service.adapter.rest.dto;

import com.sprintmodus.auth_service.application.dto.RegisteredOrganization;
import com.sprintmodus.auth_service.domain.model.Plan;

public record RegisterOrganizationResponse(String organizationCode, String organizationName, Plan plan) {

	public static RegisterOrganizationResponse from(RegisteredOrganization registered) {
		return new RegisterOrganizationResponse(registered.organizationCode(), registered.organizationName(),
				registered.plan());
	}

}

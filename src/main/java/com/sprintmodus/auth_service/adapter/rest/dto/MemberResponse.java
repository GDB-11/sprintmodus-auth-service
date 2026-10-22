package com.sprintmodus.auth_service.adapter.rest.dto;

import java.time.Instant;
import java.util.UUID;

import com.sprintmodus.auth_service.application.dto.Member;
import com.sprintmodus.auth_service.domain.model.OrganizationRole;

public record MemberResponse(UUID userCode, String fullName, String email, OrganizationRole role, boolean isActive,
		Instant lastLoginAt) {

	public static MemberResponse from(Member member) {
		return new MemberResponse(member.userCode(), member.fullName(), member.email(), member.role(), member.active(),
				member.lastLoginAt());
	}

}

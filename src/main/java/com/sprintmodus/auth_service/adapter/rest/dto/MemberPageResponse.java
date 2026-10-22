package com.sprintmodus.auth_service.adapter.rest.dto;

import java.util.List;

import com.sprintmodus.auth_service.application.dto.MemberPage;

/** {@code count} is the number of active users, {@code maxUsers} the plan's limit; {@code total} counts every listed member. */
public record MemberPageResponse(List<MemberResponse> items, int page, int size, long total, long count, int maxUsers) {

	public static MemberPageResponse from(MemberPage page) {
		return new MemberPageResponse(page.items().stream().map(MemberResponse::from).toList(), page.page(), page.size(),
				page.total(), page.activeCount(), page.maxUsers());
	}

}

package com.sprintmodus.auth_service.application.service;

import org.springframework.stereotype.Service;

import com.sprintmodus.auth_service.application.dto.MemberPage;
import com.sprintmodus.auth_service.application.error.MemberError;
import com.sprintmodus.auth_service.application.port.persistence.TenantUserRepository;
import com.sprintmodus.common_lib.result.Result;

/** The organization's members (active and deactivated) for an OWNER or ADMIN, with the seat usage. */
@Service
public class ListUsersUseCase {

	static final int MAX_PAGE_SIZE = 100;

	private final MemberCallerResolver callers;

	private final TenantUserRepository users;

	public ListUsersUseCase(MemberCallerResolver callers, TenantUserRepository users) {
		this.callers = callers;
		this.users = users;
	}

	public Result<MemberPage, MemberError> execute(String token, int page, int size) {
		return callers.resolveManager(token).map(caller -> {
			int safePage = Math.max(page, 0);
			int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
			var found = users.list(caller.organization().tenantId(), safePage, safeSize);
			return new MemberPage(found.items(), safePage, safeSize, found.total(), found.activeCount(),
					caller.subscription().maxUsers());
		});
	}

}

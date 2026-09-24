package com.sprintmodus.auth_service.domain.model;

/** Subscription tiers and the limits each one grants. The limits are copied into the JWT at login. */
public enum Plan {

	FREE(1, 5, 100),
	PRO(10, 50, 5_000),
	ENTERPRISE(999, 999, 100_000);

	private final int maxProjects;

	private final int maxUsers;

	private final int maxStorageMB;

	Plan(int maxProjects, int maxUsers, int maxStorageMB) {
		this.maxProjects = maxProjects;
		this.maxUsers = maxUsers;
		this.maxStorageMB = maxStorageMB;
	}

	public int maxProjects() {
		return maxProjects;
	}

	public int maxUsers() {
		return maxUsers;
	}

	public int maxStorageMB() {
		return maxStorageMB;
	}

}

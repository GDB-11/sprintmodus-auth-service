package com.sprintmodus.auth_service.application.dto;

import java.util.List;

/**
 * One page of members plus the seat usage: {@code activeCount} of {@code maxUsers} (the limit read from the master
 * subscription, not from the caller's token).
 */
public record MemberPage(List<Member> items, int page, int size, long total, long activeCount, int maxUsers) {
}

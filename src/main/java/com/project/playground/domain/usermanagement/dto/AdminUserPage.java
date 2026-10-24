package com.project.playground.domain.usermanagement.dto;

import lombok.*;
import java.util.List;

/** 최신 회원부터 번호로 탐색하며 마지막 페이지 이동에 필요한 동일 검색 조건의 집계를 함께 반환한다. */
@Getter @Builder
public class AdminUserPage {
    private final List<AdminUserSummary> items;
    private final int page;
    private final int size;
    private final int totalPages;
    private final long totalElements;
}

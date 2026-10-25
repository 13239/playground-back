package com.project.playground.domain.game.dto.response;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

/** 관리자 그리드는 번호 페이지와 전체 노출 순서 버전을 함께 사용한다. */
@Getter @Builder
public class AdminCatalogPageResponse<T> {
    private final List<T> items;
    private final int page;
    private final int size;
    private final int totalPages;
    private final long totalElements;
    private final long orderVersion;
}

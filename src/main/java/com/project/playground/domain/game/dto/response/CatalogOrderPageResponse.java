package com.project.playground.domain.game.dto.response;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

/** 페이지 번호와 전체 순서 버전을 함께 내려 여러 페이지의 오래된 순서를 섞지 않게 한다. */
@Getter @Builder
public class CatalogOrderPageResponse {
    private final List<CatalogOrderItemResponse> items;
    private final int page;
    private final int size;
    private final int totalPages;
    private final long totalElements;
    private final long version;
}

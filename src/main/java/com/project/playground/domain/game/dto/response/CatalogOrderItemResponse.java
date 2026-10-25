package com.project.playground.domain.game.dto.response;

import lombok.Builder;
import lombok.Getter;

/** 순서 변경에 필요한 행만 조회하며 게임 설명·모드·장르 컬렉션은 함께 로딩하지 않는다. */
@Getter @Builder
public class CatalogOrderItemResponse {
    private final String code;
    private final String name;
    private final int sortOrder;
    private final long version;
}

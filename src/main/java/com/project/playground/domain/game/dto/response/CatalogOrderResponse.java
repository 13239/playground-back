package com.project.playground.domain.game.dto.response;

import lombok.Builder;
import lombok.Getter;

/** 저장 후 다음 이동 요청과 목록 재조회에 사용할 전체 순서 버전이다. */
@Getter @Builder
public class CatalogOrderResponse {
    private final long version;
}

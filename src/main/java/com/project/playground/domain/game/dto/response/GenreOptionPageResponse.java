package com.project.playground.domain.game.dto.response;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

/** 장르 선택기는 제한된 검색 결과를 커서로 이어 읽으며 상세·집계 데이터는 포함하지 않는다. */
@Getter @Builder
public class GenreOptionPageResponse {
    private final List<GenreOptionResponse> items;
    private final String nextCursor;
}

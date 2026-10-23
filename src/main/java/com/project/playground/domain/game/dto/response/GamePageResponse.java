package com.project.playground.domain.game.dto.response;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

/** 전체 count와 무제한 offset 대신 정렬 순서·ID 커서를 사용한다. */
@Getter @Builder
public class GamePageResponse {
    private final List<GameResponse> items;
    private final String nextCursor;
}

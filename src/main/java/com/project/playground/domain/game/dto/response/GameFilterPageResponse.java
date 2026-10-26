package com.project.playground.domain.game.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** 홈과 게임 편집에서 같은 필터 목록을 커서로 읽는다. */
@Getter @Builder
public class GameFilterPageResponse {
    private final List<GameFilterResponse> items;
    private final String nextCursor;
}

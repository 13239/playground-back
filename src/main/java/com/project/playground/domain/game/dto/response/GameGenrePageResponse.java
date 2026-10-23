package com.project.playground.domain.game.dto.response;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

/** 장르 수가 늘어도 검색과 커서로 이어서 읽는다. */
@Getter @Builder
public class GameGenrePageResponse {
    private final List<GameGenreResponse> items;
    private final String nextCursor;
}

package com.project.playground.domain.game.dto.internal;

import com.project.playground.domain.game.enums.GraphicsType;
import lombok.Builder;
import lombok.Getter;

/** 여러 모드 조건은 같은 모드의 EXISTS 안에서 모두 만족해야 한다. */
@Getter @Builder
public class CatalogQuery {
    public enum Players { SINGLE, TWO, GROUP, MULTIPLAYER }
    private final String query;
    private final String genre;
    private final GraphicsType graphics;
    private final Players players;
    private final Integer humanPlayers;
    private final boolean newOnly;
    private final String cursor;
    @Builder.Default private final int limit = 40;
}

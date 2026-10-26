package com.project.playground.domain.game.dto.response;

import com.project.playground.domain.game.entity.GameFilter;
import com.project.playground.domain.game.enums.*;
import lombok.Builder;
import lombok.Getter;

/** 표시 정보와 선택 키를 함께 제공하고 연결 수는 명시적인 게임 라벨 선택만 집계한다. */
@Getter @Builder
public class GameFilterResponse {
    private final String code, labelCode, name, icon;
    private final GameFilterType type;
    private final boolean enabled;
    private final int sortOrder;
    private final long version, gameCount;
    private final Integer minPlayers, maxPlayers;
    private final GraphicsType graphicsType;
    public static GameFilterResponse from(GameFilter f, long count) {
        return builder().code(f.getCode()).labelCode(f.getLabelCode()).name(f.getName()).type(f.getType()).icon(f.getIcon())
                .enabled(f.isEnabled()).sortOrder(f.getSortOrder()).version(f.getVersion()).gameCount(count)
                .minPlayers(f.getMinPlayers()).maxPlayers(f.getMaxPlayers()).graphicsType(f.getGraphicsType()).build();
    }
}

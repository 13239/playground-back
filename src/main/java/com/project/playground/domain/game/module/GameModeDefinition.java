package com.project.playground.domain.game.module;

import com.project.playground.domain.game.enums.*;
import lombok.Builder;
import lombok.Getter;

/** 배포된 구현이 실제로 처리할 수 있는 범위다. 관리자가 임의 실행 코드나 경로를 입력하지 않는다. */
@Getter
@Builder
public class GameModeDefinition {
    private final String code;
    private final GraphicsType graphicsType;
    private final ConnectionType connectionType;
    private final InteractionType interactionType;
    private final int minHumanPlayers;
    private final int maxHumanPlayers;
    private final int version;
}

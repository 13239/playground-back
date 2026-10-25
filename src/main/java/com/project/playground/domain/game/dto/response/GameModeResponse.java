package com.project.playground.domain.game.dto.response;

import com.project.playground.domain.game.entity.GameMode;
import com.project.playground.domain.game.enums.*;
import lombok.Builder;
import lombok.Getter;

/** 모드 운영 상태와 구현 호환 여부를 전달하고 실행 경로는 내려주지 않는다. */
@Getter @Builder
public class GameModeResponse {
    private final String code;
    private final String name;
    private final GraphicsType graphicsType;
    private final ConnectionType connectionType;
    private final InteractionType interactionType;
    private final int minHumanPlayers;
    private final int maxHumanPlayers;
    private final int definitionVersion;
    private final boolean published;
    private final GameAvailability availability;
    private final String unavailableMessage;
    private final boolean supported;
    private final long version;
    public static GameModeResponse from(GameMode mode, boolean supported) {
        return builder().code(mode.getCode()).name(mode.getName()).graphicsType(mode.getGraphicsType())
                .connectionType(mode.getConnectionType()).interactionType(mode.getInteractionType())
                .minHumanPlayers(mode.getMinHumanPlayers()).maxHumanPlayers(mode.getMaxHumanPlayers()).definitionVersion(mode.getDefinitionVersion())
                .published(mode.isPublished()).availability(mode.getAvailability()).unavailableMessage(mode.getUnavailableMessage())
                // 모드 목록은 구현 정의 순서로 조립하므로 수동 노출 순서값을 공개하지 않는다.
                .supported(supported).version(mode.getVersion()).build();
    }
}

package com.project.playground.domain.gomoku.module;

import com.project.playground.domain.game.module.*;
import com.project.playground.domain.game.enums.*;
import org.springframework.stereotype.Component;
import java.util.List;

/** 기존 AI·온라인 구현의 실제 지원 조건만 등록하며 오목 방/대국 모델은 유지한다. */
@Component
public class GomokuModule implements GameModule {
    public static final String GAME = "GOMOKU";
    public static final String AI = "AI";
    public static final String ONLINE = "ONLINE";
    @Override public String code() { return GAME; }
    @Override public List<GameModeDefinition> modes() {
        return List.of(
                GameModeDefinition.builder().code(AI).graphicsType(GraphicsType.TWO_D).connectionType(ConnectionType.LOCAL)
                        .interactionType(InteractionType.SOLO).minHumanPlayers(1).maxHumanPlayers(1).version(1).build(),
                GameModeDefinition.builder().code(ONLINE).graphicsType(GraphicsType.TWO_D).connectionType(ConnectionType.ONLINE)
                        .interactionType(InteractionType.COMPETITIVE).minHumanPlayers(2).maxHumanPlayers(2).version(1).build());
    }
}

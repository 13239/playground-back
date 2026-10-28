package com.project.playground.domain.memory;

import com.project.playground.domain.game.module.*;
import com.project.playground.domain.game.enums.*;
import org.springframework.stereotype.Component;
import java.util.List;

/** 두 모드 모두 서버가 카드와 기록을 판정하므로 ONLINE으로 등록한다. */
@Component
public class MemoryModule implements GameModule {
    @Override public String code() { return "MEMORY"; }
    @Override public List<GameModeDefinition> modes() {
        return List.of(
            GameModeDefinition.builder().code("SINGLE").graphicsType(GraphicsType.TWO_D).connectionType(ConnectionType.ONLINE)
                .interactionType(InteractionType.SOLO).minHumanPlayers(1).maxHumanPlayers(1).version(1).build(),
            GameModeDefinition.builder().code("ONLINE").graphicsType(GraphicsType.TWO_D).connectionType(ConnectionType.ONLINE)
                .interactionType(InteractionType.COMPETITIVE).minHumanPlayers(2).maxHumanPlayers(2).version(1).build());
    }
}

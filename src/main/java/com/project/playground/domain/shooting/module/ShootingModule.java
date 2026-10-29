package com.project.playground.domain.shooting.module;

import com.project.playground.domain.game.enums.*;
import com.project.playground.domain.game.module.*;

import org.springframework.stereotype.Component;

import java.util.List;

/** 1인도 서버가 판정하는 온라인 기록 도전이며 2인은 경쟁이 아닌 협동으로 등록한다. */
@Component
public class ShootingModule implements GameModule {
    @Override
    public String code() {
        return "SHOOTING";
    }

    @Override
    public List<GameModeDefinition> modes() {
        return List.of(
                mode("SINGLE", 1, InteractionType.SOLO),
                mode("COOP", 2, InteractionType.COOPERATIVE));
    }

    private GameModeDefinition mode(String code, int players, InteractionType interaction) {
        return GameModeDefinition.builder()
                .code(code)
                .graphicsType(GraphicsType.TWO_D)
                .connectionType(ConnectionType.ONLINE)
                .interactionType(interaction)
                .minHumanPlayers(players)
                .maxHumanPlayers(players)
                .version(1)
                .build();
    }
}

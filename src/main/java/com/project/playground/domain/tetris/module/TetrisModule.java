package com.project.playground.domain.tetris.module;

import com.project.playground.domain.game.enums.ConnectionType;
import com.project.playground.domain.game.enums.GraphicsType;
import com.project.playground.domain.game.enums.InteractionType;
import com.project.playground.domain.game.module.GameModeDefinition;
import com.project.playground.domain.game.module.GameModule;
import org.springframework.stereotype.Component;
import java.util.List;

/** 공통 카탈로그와 신규 실행 정책에 로컬 1인 모드만 등록한다. */
@Component
public class TetrisModule implements GameModule {
    @Override public String code() { return "TETRIS"; }
    @Override public List<GameModeDefinition> modes() {
        return List.of(GameModeDefinition.builder().code("SINGLE").graphicsType(GraphicsType.TWO_D)
                .connectionType(ConnectionType.LOCAL).interactionType(InteractionType.SOLO)
                .minHumanPlayers(1).maxHumanPlayers(1).version(1).build());
    }
}

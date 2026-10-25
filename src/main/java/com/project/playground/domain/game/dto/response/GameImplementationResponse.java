package com.project.playground.domain.game.dto.response;

import com.project.playground.domain.game.module.*;
import lombok.Builder;
import lombok.Getter;
import java.util.List;

/** 관리자에게 구현·지원 모드·등록 여부만 제공한다. 소스나 내부 실행 경로는 공개하지 않는다. */
@Getter @Builder
public class GameImplementationResponse {
    private final String code;
    private final boolean registered;
    private final List<GameModeDefinition> modes;
    public static GameImplementationResponse from(GameModule module, boolean registered) {
        return builder().code(module.code()).registered(registered).modes(List.copyOf(module.modes())).build();
    }
}

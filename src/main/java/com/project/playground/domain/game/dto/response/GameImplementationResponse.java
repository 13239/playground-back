package com.project.playground.domain.game.dto.response;

import com.project.playground.domain.game.module.*;
import lombok.Builder;
import lombok.Getter;
import java.util.*;

/** 관리자에게 등록 가능한 구현·모드·썸네일 키만 제공한다. 소스나 내부 실행 경로는 공개하지 않는다. */
@Getter @Builder
public class GameImplementationResponse {
    private final String code;
    private final Set<String> thumbnailKeys;
    private final List<GameModeDefinition> modes;
    public static GameImplementationResponse from(GameModule module) {
        return builder().code(module.code()).thumbnailKeys(Set.copyOf(module.thumbnailKeys())).modes(List.copyOf(module.modes())).build();
    }
}

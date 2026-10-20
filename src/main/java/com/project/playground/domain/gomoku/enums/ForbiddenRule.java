package com.project.playground.domain.gomoku.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/** STANDARD는 흑돌의 3·3, 4·4, 장목 금수 적용 여부이며 실제 착수 판정은 대국 단계에서 구현한다. */
public enum ForbiddenRule {
    NONE, STANDARD;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ForbiddenRule fromId(String id) {
        return id == null ? null : valueOf(id);
    }
}

package com.project.playground.domain.gomoku.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/** 방 공개 여부는 명시적인 문자열만 받으며 숫자 순번은 허용하지 않는다. */
public enum RoomVisibility {
    PUBLIC, PRIVATE;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static RoomVisibility fromId(String id) {
        return id == null ? null : valueOf(id);
    }
}

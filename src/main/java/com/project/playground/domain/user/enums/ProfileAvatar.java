package com.project.playground.domain.user.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/** 업로드 경로나 URL 대신 서버가 제공하는 열 가지 캐릭터 식별자만 계정에 저장한다. */
public enum ProfileAvatar {
    CAT,
    DOG,
    RABBIT,
    FOX,
    BEAR,
    PANDA,
    OTTER,
    PENGUIN,
    FROG,
    DUCK;

    /** 숫자 순번으로 열거형을 선택하는 역직렬화도 거절하고 명시적인 식별자만 허용한다. */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ProfileAvatar fromId(String id) {
        if (id == null) return null;
        try {
            return valueOf(id);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("제공되지 않는 프로필 캐릭터입니다.");
        }
    }
}

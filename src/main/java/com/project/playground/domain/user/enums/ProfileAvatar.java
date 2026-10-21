package com.project.playground.domain.user.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/** 업로드 경로나 URL 대신 서버가 제공하는 열다섯 가지 캐릭터 식별자만 계정에 저장한다. */
public enum ProfileAvatar {
    CAT,
    DOG,
    RABBIT,
    FOX,
    BEAR,
    PANDA,
    OTTER,
    PENGUIN,
    // 기존 개구리 선택값은 V3 마이그레이션에서 새 아기 양 식별자로 이전한다.
    SHEEP,
    DUCK,
    // 기존 회원의 식별자는 유지하고 추가된 다섯 캐릭터도 같은 검증·저장 경로를 사용한다.
    GRAY_CAT,
    RACCOON,
    HAMSTER,
    KOALA,
    RED_PANDA;

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

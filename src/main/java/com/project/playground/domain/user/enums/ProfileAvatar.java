package com.project.playground.domain.user.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/** 업로드 경로나 URL 대신 최종 확정한 스무 가지 캐릭터 식별자만 계정에 저장한다. */
public enum ProfileAvatar {
    SPROUT,
    CLOUD,
    JELLY_STAR,
    LILAC_ROBOT,
    HOODIE_PENGUIN,
    HEADPHONE_DINO,
    SEAL,
    RAIN_HAT_FROG,
    RAINDROP,
    WIZARD_MOCHI,
    MINT_ROBOT,
    HOODIE_HAMSTER,
    HEADPHONE_DRAGON,
    HEADPHONE_ALIEN,
    CREAM_ROBOT,
    PEACH_SPROUT,
    BLUE_OWL,
    GAMER_GHOST,
    HEADPHONE_WHALE,
    SNOWMAN;

    /** 시안 5번 새싹 요정을 신규 가입·공개 프로필 대체값의 공통 기본 캐릭터로 사용한다. */
    public static final ProfileAvatar DEFAULT = SPROUT;

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

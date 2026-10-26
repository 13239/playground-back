package com.project.playground.domain.game.support;

/** 라벨 이름 대신 유형·코드를 저장하고 표시할 때 현재 장르·모드의 지원 정보와 대조한다. */
public final class GameCardLabels {
    public static final int MAX_LABELS = 128;
    public static final String CODE_PATTERN = "(?:GRAPHICS:[A-Z][A-Z0-9_]{0,63}|GENRE:[A-Z][A-Z0-9_]{0,63}|PLAYERS:[A-Z][A-Z0-9_]{0,63})";

    private GameCardLabels() {}

    // 영구 식별자는 DB 필터에서 읽으며 모드 조건으로 라벨 코드를 생성하지 않는다.
}

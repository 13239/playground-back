package com.project.playground.domain.game.module;

import java.util.List;

/** 새 게임은 이 등록 정의와 전용 구현을 추가한다. 공통 인증·권한·카탈로그는 재작성하지 않고 이미지는 프론트 구현에 연결한다. */
public interface GameModule {
    String code();
    List<GameModeDefinition> modes();
}

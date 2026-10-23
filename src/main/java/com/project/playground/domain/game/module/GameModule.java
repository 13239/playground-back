package com.project.playground.domain.game.module;

import java.util.List;
import java.util.Set;

/** 새 게임은 이 등록 정의와 전용 구현을 추가한다. 공통 인증·권한·카탈로그는 재작성하지 않는다. */
public interface GameModule {
    String code();
    Set<String> thumbnailKeys();
    List<GameModeDefinition> modes();
}

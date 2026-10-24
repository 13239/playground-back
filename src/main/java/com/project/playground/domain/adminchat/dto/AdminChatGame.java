package com.project.playground.domain.adminchat.dto;

import lombok.Builder;
import lombok.Getter;

/** 새 게임 추가 시 화면 코드를 고치지 않도록 DB 카탈로그에서 선택지를 읽는다. */
@Getter @Builder
public class AdminChatGame {
    private final String code;
    private final String name;
}

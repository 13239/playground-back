package com.project.playground.domain.adminchat.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import java.time.Instant;

/** 채팅 서버의 저장 스냅샷에 카탈로그의 게임 이름만 보충한다. 작성자 정보는 덮어쓰지 않는다. */
@Getter @Builder(toBuilder = true) @Jacksonized
public class AdminChatMessage {
    private final String id;
    private final Instant sentAt;
    private final Long authorId;
    private final String authorNickname;
    private final String authorAvatarId;
    private final String scope;
    private final String gameCode;
    private final String gameName;
    private final Long roomId;
    private final String content;
}

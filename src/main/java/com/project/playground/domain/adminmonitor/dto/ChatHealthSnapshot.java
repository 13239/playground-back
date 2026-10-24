package com.project.playground.domain.adminmonitor.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import java.time.Instant;

/** 채팅 서버의 운영 연결 확인 결과만 역직렬화한다. */
@Getter @Builder @Jacksonized
public class ChatHealthSnapshot {
    private final String state;
    private final Instant checkedAt;
}

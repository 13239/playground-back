package com.project.playground.global.security.session;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class RedisSession {

    private final UUID sessionId;

    private final Long id;

    private final String username;

    private final String refreshToken;

    private final boolean keepLoggedIn;

    private final Instant createdAt;

    private final Instant expiresAt;

    // 검증한 세션의 만료 정보도 Principal에 전달하여 인증 이후의 중복 Redis 조회를 피한다.
    public Session toSession() {
        return Session.builder()
                .sessionId(sessionId)
                .id(id)
                .username(username)
                .keepLoggedIn(keepLoggedIn)
                .expiresAt(expiresAt)
                .build();
    }
}

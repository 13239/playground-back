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

    public Session toSession() {
        return Session.builder()
                .sessionId(sessionId)
                .id(id)
                .username(username)
                .keepLoggedIn(keepLoggedIn)
                .build();
    }
}

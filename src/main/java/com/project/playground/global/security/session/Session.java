package com.project.playground.global.security.session;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * 요청마다 Redis에서 검증한 로그인 세션으로 구성하는 인증 정보다.
 * 사용자 판별을 위해 user 테이블을 다시 조회하거나 이전 요청의 인증 정보를 재사용하지 않는다.
 */
@Getter
@Builder
public class Session {

    private final UUID sessionId;

    private final Long id;

    private final String username;

    private final boolean keepLoggedIn;

    // 인증 시 Redis에서 읽은 세션 만료 시각이며 Access Token의 만료 시각과는 구분한다.
    private final Instant expiresAt;
}

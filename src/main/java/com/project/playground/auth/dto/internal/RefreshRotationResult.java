package com.project.playground.auth.dto.internal;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Lua가 검증한 암호문과 현재 Redis 세션 정책을 함께 반환한다.
 */
@Getter
@Builder
public class RefreshRotationResult {

    private final String encryptedTokens;

    private final boolean keepLoggedIn;

    private final Instant sessionExpiresAt;
}

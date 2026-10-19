package com.project.playground.auth.dto.internal;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

/**
 * 중복 갱신에 공유할 토큰 쌍. Redis에는 이 DTO를 인증 암호화한 값만 저장한다.
 */
@Getter
@Builder
@Jacksonized
public class RefreshTokenPair {

    private final String accessToken;

    private final String refreshToken;

    // 공유 응답을 늦게 받아도 Access Token 수명을 다시 시작하지 않도록 절대 만료를 보관한다.
    private final Instant accessTokenExpiresAt;
}

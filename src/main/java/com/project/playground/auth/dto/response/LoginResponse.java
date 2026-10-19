package com.project.playground.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class LoginResponse {

    private String accessToken;

    private String refreshToken;

    private String tokenType;

    // Access Token의 만료 시간 (초)
    private long expiresIn;

    // 중복 갱신 응답이나 통신 지연으로 Access Token 수명을 늘려 계산하지 않도록 절대 만료 시각을 제공한다.
    private Instant accessTokenExpiresAt;

    // 로그인 유지 여부
    private boolean keepLoggedIn;

    // Redis 로그인 세션의 만료 시각
    private Instant sessionExpiresAt;
}

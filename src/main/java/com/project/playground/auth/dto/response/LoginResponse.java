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

    // 로그인 유지 여부
    private boolean keepLoggedIn;

    // Redis 로그인 세션의 만료 시각
    private Instant sessionExpiresAt;
}

package com.project.playground.auth.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

@Getter
@Builder
@Jacksonized // 동시 재로그인의 공유 결과를 암호문에서 복원할 때도 Builder를 사용한다.
public class LoginResponse {

    private String accessToken;

    private String refreshToken;

    // BFF 쿠키가 저장할 키 바인딩 메타데이터이며 로그인 JSON으로 브라우저에 전달하지 않는다.
    private String browserKeyThumbprint;

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

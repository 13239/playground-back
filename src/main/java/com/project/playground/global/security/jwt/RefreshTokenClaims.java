package com.project.playground.global.security.jwt;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/** 갱신 전용 서명·발급자·대상·타입 검증을 통과한 토큰의 세션과 세대 정보다. */
@Getter
@Builder
public class RefreshTokenClaims {

    private final UUID sessionId;

    private final long generation;
}

package com.project.playground.auth.dto.internal;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** 기존 세션 교체를 요청하는 내부 증명이며 Redis에서 토큰 해시를 원자적으로 재검증한다. */
@Getter
@Builder
public class LoginSessionReplacement {

    private final UUID sessionId;

    // 토큰 원문은 Redis Key·명령 인자에 전달하지 않는다.
    private final String refreshTokenHash;

    // 선택 정보이며 없으면 현재 Refresh Token 해시가 직접 일치할 때만 기존 세션을 교체한다.
    private final @Nullable String refreshRequestHash;
}

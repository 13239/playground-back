package com.project.playground.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/** 30초짜리 일회용 증명만 공개하며 로그인 토큰·회원 ID·세션 상세는 반환하지 않는다. */
@Getter
@Builder
public class ChatTicketResponse {
    private final String ticket;
    private final Instant serverTime;
    private final Instant expiresAt;
    private final String websocketUrl;
    private final boolean proofRequired;
}

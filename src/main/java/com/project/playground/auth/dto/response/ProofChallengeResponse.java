package com.project.playground.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/** nonce는 공개 가능한 일회 요청 증명의 재료이며 인증 토큰이나 사용자 정보는 포함하지 않는다. */
@Getter
@Builder
public class ProofChallengeResponse {

    private final String nonce;

    // 클라이언트 시계 대신 서버 시각을 사용해 여러 서버·탭의 작은 시간 차이를 흡수한다.
    private final Instant serverTime;

    private final Instant expiresAt;
}

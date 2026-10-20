package com.project.playground.auth.dto.response;

import com.project.playground.global.security.session.Session;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * 현재 로그인 세션 확인과 로그인 상태 복원에 필요한 정보만 제공한다.
 */
@Getter
@Builder
public class CurrentSessionResponse {

    // 현재 요청에서 Redis로 검증한 사용자 식별 정보
    private final Long id;

    private final String username;

    // 화면이 확인한 세션과 변경 요청의 세션을 대조한다. 인증 토큰이나 권한 증명으로 사용하지 않는다.
    private final UUID sessionId;

    // 로그인 유지 여부와 Redis 세션의 만료 시각
    private final boolean keepLoggedIn;

    private final Instant sessionExpiresAt;

    /**
     * 검증된 인증 정보를 응답으로 변환하며 DB 조회나 세션 만료 시간 연장을 수행하지 않는다.
     */
    public static CurrentSessionResponse from(Session session) {
        return CurrentSessionResponse.builder()
                .id(session.getId())
                .username(session.getUsername())
                .sessionId(session.getSessionId())
                .keepLoggedIn(session.isKeepLoggedIn())
                .sessionExpiresAt(session.getExpiresAt())
                .build();
    }
}

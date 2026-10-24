package com.project.playground.domain.adminmonitor.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.Instant;
import java.util.List;

/** 내부 접속 수집 계약이다. 회원 식별자·인증 정보는 관리 화면 응답에 담지 않는다. */
@Getter @Builder
public class PresenceObservation {
    private final Instant checkedAt;
    private final boolean ready;
    private final Long count;
    private final List<PendingPeak> pending;

    /** 비교 후 제거할 원문 값은 Redis 수집 처리 안에서만 사용한다. */
    @Getter @Builder
    public static class PendingPeak {
        private final String bucket;
        private final String value;
        private final Instant sampledAt;
        private final Instant observedAt;
        private final long peak;
    }
}

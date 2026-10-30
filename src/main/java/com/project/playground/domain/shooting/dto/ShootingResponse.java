package com.project.playground.domain.shooting.dto;

import lombok.*;

import java.util.List;

/** 인증 원문을 포함하는 내부 신원은 게임 서버에만 전달하고 공개 응답은 별도 DTO로 제한한다. */
public final class ShootingResponse {
    private ShootingResponse() {}

    @Getter
    @Builder
    public static class Ticket {
        private final String ticket;
        private final String endpoint;
        private final int expiresIn;
        private final boolean proofRequired;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Identity {
        private Long userId;
        private String sessionId;
        private String browserKey;
        private String nickname;
        private String avatar;
        private String mode;
        private String origin;
        private boolean proofRequired;
        // 이 내부 권한은 Redis 일회용 티켓으로만 게임 서버에 전달하며 공개 티켓 응답에는 싣지 않는다.
        private String invitedRoomCode;
    }

    @Getter
    @Builder
    public static class Ranking {
        private final long rank;
        private final String nickname;
        private final String teammate;
        private final int stage;
        private final long combatMillis;
        private final boolean self;
    }

    @Getter
    @Builder
    public static class Overview {
        private final String mode;
        private final int page;
        private final int totalPages;
        private final int bestStage;
        private final long bestMillis;
        private final List<Ranking> rankings;
    }
}

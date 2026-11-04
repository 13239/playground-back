package com.project.playground.domain.shooting.dto;

import com.project.playground.domain.shooting.service.ShootingStageRules;

import jakarta.validation.constraints.*;

import lombok.*;

/** 브라우저 입력과 신뢰된 게임 서버 입력을 구분하며 외부 API는 점수나 회원 식별자를 받지 않는다. */
public final class ShootingRequest {
    private ShootingRequest() {}

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Overview {
        @Builder.Default
        // 맵 허용 목록은 티켓·랭킹·기록 저장이 같은 규칙을 사용한다.
        @Pattern(regexp = ShootingStageRules.MAP_PATTERN)
        private String mapId = "CAVE";
        @NotBlank
        @Pattern(regexp = "SINGLE|COOP")
        private String mode;

        @Min(1)
        @Max(10)
        private int page;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Ticket {
        // 생성할 맵은 인증 티켓에 묶는다. 참가 시 실제 방의 맵 해금도 게임 서버에서 확인한다.
        @Pattern(regexp = ShootingStageRules.MAP_PATTERN)
        private String mapId;
        @NotBlank
        @Pattern(regexp = "SINGLE|COOP")
        private String mode;

        // 초대·링크·코드 참여는 특정 방에 묶인 일회용 참가권으로 비밀번호 없이 입장한다.
        @Pattern(regexp = "[A-Z2-9]{8}")
        private String invitedRoomCode;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Consume {
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_-]{43}")
        private String ticket;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Lease {
        @NotNull @Positive private Long userId;

        @NotBlank
        @Pattern(regexp = "[0-9a-f-]{36}")
        private String sessionId;

        @NotBlank
        @Size(max = 64)
        private String browserKey;

        @NotBlank
        @Pattern(regexp = "[A-Z2-9]{8}")
        private String roomId;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Checkpoint {
        @NotBlank @Pattern(regexp = ShootingStageRules.MAP_PATTERN)
        private String mapId;

        // 새 전투 규칙만 기록을 제출하며 과거 버전은 조회 경로에서만 호환한다.
        @Min(ShootingStageRules.VERSION) @Max(ShootingStageRules.VERSION)
        private int ruleVersion;

        private boolean completed;
        @NotBlank
        @Pattern(regexp = "[0-9a-f-]{36}")
        private String runId;

        @NotNull @Positive private Long userId;

        @NotBlank
        @Pattern(regexp = "[0-9a-f-]{36}")
        private String sessionId;

        @NotBlank
        @Pattern(regexp = "SINGLE|COOP")
        private String mode;

        @Min(0) @Max(10)
        private int clearedStage;

        @Min(0)
        @Max(9007199254740991L)
        private long combatMillis;

        @Size(max = 50)
        private String teammate;

        private boolean finished;
    }
}

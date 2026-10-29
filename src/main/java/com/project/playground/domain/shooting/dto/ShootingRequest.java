package com.project.playground.domain.shooting.dto;

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
        @NotBlank
        @Pattern(regexp = "SINGLE|COOP")
        private String mode;
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

        @Min(0)
        private int clearedStage;

        @Min(0)
        @Max(9007199254740991L)
        private long combatMillis;

        @Size(max = 50)
        private String teammate;

        private boolean finished;
    }
}

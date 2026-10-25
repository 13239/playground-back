package com.project.playground.domain.tetris.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;

/** 동작별 전용 입력만 받으며 점수·회원 ID·승패는 클라이언트 입력으로 받지 않는다. */
public final class TetrisRequest {
    private TetrisRequest() { }
    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Overview {
        @Min(1) @Max(10) private int page;
    }
    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Start {
        @NotBlank @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}") private String requestId;
    }
    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Finish {
        @NotBlank @Size(max = 36) private String runId;
        @Min(1) @Max(216000) private int ticks;
        @NotNull @Size(max = 24000) private List<@NotNull @Size(min = 2, max = 2) List<@NotNull Integer>> inputs;
    }
}

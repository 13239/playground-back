package com.project.playground.domain.tetris.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

/** 공개 DTO에는 로그인 식별자와 내부 회원 ID를 포함하지 않는다. */
public final class TetrisResponse {
    private TetrisResponse() { }
    @Getter @Builder public static class Start {
        private final String runId;
        private final long seed;
        private final int ruleVersion;
    }
    @Getter @Builder public static class Ranking {
        private final int rank;
        private final String nickname;
        private final long score;
        private final boolean self;
    }
    @Getter @Builder public static class Overview {
        private final long bestScore;
        private final Long rank;
        private final long plays;
        private final int page;
        private final int totalPages;
        private final List<Ranking> rankings;
    }
}

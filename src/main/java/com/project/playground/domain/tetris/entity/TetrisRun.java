package com.project.playground.domain.tetris.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

/** 행 잠금으로 시작·종료를 직렬화한다. 진행 중인 보드나 클라이언트 점수는 저장하지 않는다. */
@Entity @Table(name = "tbl_tetris_run") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TetrisRun {
    @Id @Column(name = "user_id") private Long userId;
    @Column(name = "run_id", length = 36) private String runId;
    @Column(name = "login_session_id", length = 36) private String loginSessionId;
    @Column(nullable = false) private long seed;
    @Column(name = "started_at") private Instant startedAt;
    @Column(nullable = false) private boolean finished;
    // 배포 전에 다른 크기로 시작한 판을 현재 규칙으로 재생해 잘못 집계하지 않는다.
    @Column(name = "rule_version", nullable = false) private int ruleVersion;

    public void begin(String id, String session, long seed, Instant now, int ruleVersion) {
        this.runId = id; this.loginSessionId = session; this.seed = seed; this.startedAt = now; this.finished = false;
        this.ruleVersion = ruleVersion;
    }
    public void finish() { this.finished = true; }
}

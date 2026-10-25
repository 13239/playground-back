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

    public void begin(String id, String session, long seed, Instant now) {
        this.runId = id; this.loginSessionId = session; this.seed = seed; this.startedAt = now; this.finished = false;
    }
    public void finish() { this.finished = true; }
}

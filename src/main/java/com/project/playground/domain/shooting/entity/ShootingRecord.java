package com.project.playground.domain.shooting.entity;

import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;

import jakarta.persistence.*;

import lombok.*;

import java.time.Instant;

/** 진행 복구 데이터 없이 서버가 확인한 마지막 클리어 기록만 보존한다. */
@Entity
@Table(name = "tbl_shooting_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShootingRecord {
    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "run_id", nullable = false, length = 36)
    private String runId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 10)
    private String mode;

    @Column(nullable = false)
    private int stage;

    @Column(name = "combat_millis", nullable = false)
    private long combatMillis;

    @Column(length = 50)
    private String teammate;

    @Column(nullable = false)
    private boolean finished;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "achieved_at", nullable = false)
    private Instant achievedAt;

    /** 지연된 이전 클리어는 무시하고 퇴장·종료가 확정된 기록은 이후 갱신하지 않는다. */
    public void checkpoint(int stage, long millis, String teammate, boolean finish) {
        if (finished || stage < this.stage) return;
        if (stage == this.stage && millis != combatMillis
                || stage > this.stage && millis < combatMillis)
            throw new BusinessException(ErrorCode.SHOOTING_RECORD_INVALID);
        if (stage > this.stage) {
            this.stage = stage;
            combatMillis = millis;
            achievedAt = Instant.now();
        }
        this.teammate = teammate;
        if (finish) finished = true;
    }
}

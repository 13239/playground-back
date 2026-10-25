package com.project.playground.domain.tetris.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 최고 점수와 시작 횟수는 각 판의 시작·검증 완료 트랜잭션 안에서 원자적으로 갱신한다. */
@Entity @Table(name = "tbl_tetris_player_stats") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TetrisPlayerStats {
    @Id @Column(name = "user_id") private Long userId;
    @Column(name = "best_score", nullable = false) private long bestScore;
    @Column(nullable = false) private long plays;
}

package com.project.playground.domain.gomoku.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 기능 도입 이후 확정된 온라인 대국만 집계한다. AI·과거 대국은 여기에 기록하지 않는다. */
@Entity
@Table(name = "tbl_gomoku_player_stats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GomokuPlayerStats {
    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private long wins;

    @Column(nullable = false)
    private long losses;

    @Column(nullable = false)
    private long draws;
}

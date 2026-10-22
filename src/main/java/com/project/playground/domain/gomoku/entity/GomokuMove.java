package com.project.playground.domain.gomoku.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 서버에서 검증한 좌표와 좌석만 저장하며 돌 색은 해당 대국의 흑 좌석에서 계산한다. */
@Embeddable @Getter @Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GomokuMove {
    @Column(name = "board_x", nullable = false) private short x;
    @Column(name = "board_y", nullable = false) private short y;
    @Column(nullable = false) private short seat;
}

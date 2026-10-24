package com.project.playground.domain.gomoku.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 강퇴 사실을 보존해 이탈 안내를 구분하고, 선택된 경우에만 같은 방 재입장을 차단한다. */
@Entity
@Table(name = "tbl_gomoku_room_kick", uniqueConstraints = @UniqueConstraint(name = "uk_gomoku_room_kick_user", columnNames = {"room_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GomokuRoomKick {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private GomokuRoom room;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    // 기존 강퇴는 기본값 true로 유지한다. 재참가 허용 강퇴 이력은 재입장 시 삭제한다.
    @Column(name = "reentry_blocked", nullable = false)
    private boolean reentryBlocked;

    @Builder
    public GomokuRoomKick(GomokuRoom room, Long userId, boolean reentryBlocked) {
        this.room = room;
        this.userId = userId;
        this.reentryBlocked = reentryBlocked;
    }
}

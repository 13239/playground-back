package com.project.playground.domain.gomoku.entity;

import com.project.playground.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** 계정당 한 대기실·좌석당 한 참가자를 DB 제약으로 보장하고 재접속에는 같은 참가 상태를 사용한다. */
@Entity
@Table(name = "tbl_gomoku_room_participant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GomokuRoomParticipant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private GomokuRoom room;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "membership_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String membershipId;
    @Column(nullable = false)
    private short seat;
    @Column(nullable = false)
    private boolean ready;
    @Column(name = "joined_at_micros", nullable = false)
    private long joinedAtMicros;
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Builder
    public GomokuRoomParticipant(GomokuRoom room, User user, short seat, long joinedAtMicros) {
        this.room = room;
        this.user = user;
        this.seat = seat;
        this.joinedAtMicros = joinedAtMicros;
        this.membershipId = UUID.randomUUID().toString();
        this.lastSeenAt = Instant.now();
    }

    /** 준비 요청은 토글 대신 원하는 값을 받아 중복 전송에도 같은 결과를 유지한다. */
    public void ready(boolean ready) { this.ready = ready; }

    /** 읽기 요청마다 UPDATE하지 않고 15초 이상 지난 접속 확인만 저장한다. */
    public void seen(Instant now) {
        if (lastSeenAt.isBefore(now.minusSeconds(15))) lastSeenAt = now;
    }
}

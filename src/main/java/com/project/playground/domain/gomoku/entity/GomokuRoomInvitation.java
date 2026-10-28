package com.project.playground.domain.gomoku.entity;

import com.project.playground.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

/** 공개 초대 UUID와 서버 기한을 보존한다. 방장 재참가·규칙 변경 후에는 과거 초대를 재사용하지 않는다. */
@Entity
@Table(name = "tbl_gomoku_invitation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GomokuRoomInvitation {
    public enum State { PENDING, ACCEPTED, REJECTED, CANCELLED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String publicId;
    @Column(name = "request_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String requestId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "room_id", nullable = false)
    private GomokuRoom room;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "inviter_id", nullable = false)
    private User inviter;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "target_id", nullable = false)
    private User target;
    @Column(name = "host_membership_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String hostMembershipId;
    @Column(name = "room_revision", nullable = false)
    private long roomRevision;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12, columnDefinition = "varchar(12)")
    private State state;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    @Column(name = "accepted_membership_id", length = 36, columnDefinition = "char(36)")
    private String acceptedMembershipId;

    @Builder
    public GomokuRoomInvitation(GomokuRoom room, User inviter, User target, String requestId,
                               String hostMembershipId, Instant now) {
        this.publicId = UUID.randomUUID().toString();
        this.requestId = requestId;
        this.room = room;
        this.inviter = inviter;
        this.target = target;
        this.hostMembershipId = hostMembershipId;
        this.roomRevision = room.getRevision();
        this.state = State.PENDING;
        // MySQL DATETIME(6)과 같은 정밀도를 사용해 중복 요청 응답의 기한도 동일하게 유지한다.
        this.createdAt = now.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        this.expiresAt = createdAt.plusSeconds(15);
    }

    /** 기한과 같은 시각도 만료다. 브라우저 시계나 타이머 실행 여부를 신뢰하지 않는다. */
    public boolean expired(Instant now) { return !expiresAt.isAfter(now); }

    public void resolve(State next, Instant now, String membershipId) {
        if (state != State.PENDING) return;
        state = next;
        resolvedAt = now;
        acceptedMembershipId = next == State.ACCEPTED ? membershipId : null;
    }
}

package com.project.playground.domain.gomoku.entity;

import com.project.playground.domain.gomoku.enums.ForbiddenRule;
import com.project.playground.domain.gomoku.enums.RoomStatus;
import com.project.playground.domain.gomoku.enums.RoomVisibility;
import com.project.playground.domain.user.entity.User;
import com.project.playground.global.audit.TimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 비밀번호 해시를 포함한 엔티티를 API에 직접 반환하지 않고 공개 DTO로만 응답한다. */
@Entity
@Table(name = "tbl_gomoku_room")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GomokuRoom extends TimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_user_id", nullable = false)
    private User host;

    // 방장을 위임해도 생성 요청의 소유권과 과거 요청 ID는 바뀌지 않는다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_user_id", nullable = false)
    private User creator;

    @Column(name = "create_request_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String createRequestId;

    @Column(nullable = false, length = 30)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, columnDefinition = "varchar(10)")
    private RoomVisibility visibility;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "invite_code", nullable = false, length = 6, columnDefinition = "char(6)")
    private String inviteCode;

    @Column(name = "undo_limit", nullable = false)
    private short undoLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "forbidden_rule", nullable = false, length = 10, columnDefinition = "varchar(10)")
    private ForbiddenRule forbiddenRule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, columnDefinition = "varchar(10)")
    private RoomStatus status = RoomStatus.WAITING;

    @Column(name = "player_count", nullable = false)
    private short playerCount = 1;

    // 방 행 잠금 안에서 참가·준비 변경 순서를 증가시켜 늦은 응답과 Redis 권한 갱신을 구분한다.
    @Column(nullable = false)
    private long revision;

    // 인원·판 크기·턴 제한·선공 결정은 클라이언트 입력으로 받지 않는다.
    @Builder
    public GomokuRoom(User host, String createRequestId, String title, RoomVisibility visibility,
                      String passwordHash, String inviteCode, short undoLimit, ForbiddenRule forbiddenRule) {
        this.host = host;
        this.creator = host;
        this.createRequestId = createRequestId;
        this.title = title;
        this.visibility = visibility;
        this.passwordHash = passwordHash;
        this.inviteCode = inviteCode;
        this.undoLimit = undoLimit;
        this.forbiddenRule = forbiddenRule;
    }

    /** 빈 대기방을 닫아 다른 방 생성이 가능하게 하며 과거 요청 식별자는 이력으로 보존한다. */
    public void close() {
        status = RoomStatus.CLOSED;
        playerCount = 0;
        revision++;
        delete();
    }

    /** 정원과 준비 상태 변경은 실제 참가자 목록을 기준으로만 반영한다. */
    public void participantsChanged(int count) {
        playerCount = (short) count;
        revision++;
    }

    /** 방장이 퇴장하면 남은 참가자가 방을 이어받으며 방 규칙은 변경하지 않는다. */
    public void transferHost(User successor) { host = successor; }
}

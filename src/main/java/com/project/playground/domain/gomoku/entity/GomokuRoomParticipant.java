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
    // 새로고침은 새 페이지가 참가를 이어받으며 이전 페이지의 지연 종료 요청을 무효화한다.
    @Column(name = "page_id", length = 36, columnDefinition = "char(36)")
    private String pageId;
    // 한 브라우저 탭은 새로고침마다 다른 pageId를 사용하되 같은 tabId로 참가를 이어받는다.
    @Column(name = "tab_id", length = 36, columnDefinition = "char(36)")
    private String tabId;
    // 서버가 검증한 로그인에 점유를 묶어, 종료 알림이 유실돼도 만료된 로그인에서 새 로그인으로 복귀할 수 있다.
    @Column(name = "login_session_id", length = 36, columnDefinition = "char(36)")
    private String loginSessionId;
    private Instant exitDeadline;

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

    /** 같은 페이지의 중복 OPEN도 허용하되, 닫힘 예약은 새 문서의 정상 OPEN에만 취소한다. */
    /** 같은 브라우저 탭의 새 문서는 이전 문서를 교체한다. 다른 탭은 기존 연결이 살아 있으면 입장할 수 없다. */
    public boolean openPage(String nextLoginSessionId, Instant loginCreatedAt, String nextTabId, String nextPageId, Instant now) {
        // 기존 행은 세션 식별자가 없으므로 마지막 접속 이후 생성된 로그인만 이전 점유를 즉시 이어받는다.
        // 다른 로그인으로의 인계는 서비스에서 현재 로그인 유효성과 이전 로그인 만료를 확인한 뒤에만 호출한다.
        boolean newLogin = loginSessionId != null ? !loginSessionId.equals(nextLoginSessionId)
                : lastSeenAt.isBefore(loginCreatedAt);
        if (!newLogin && tabId != null && !tabId.equals(nextTabId) && exitDeadline == null
                && !lastSeenAt.isBefore(now.minusSeconds(90))) return false;
        // 응답 유실로 동일 OPEN을 재시도해도 다른 탭으로 판단하지 않는다.
        loginSessionId = nextLoginSessionId;
        tabId = nextTabId;
        pageId = nextPageId;
        exitDeadline = null;
        seen(now);
        return true;
    }

    /** 오래된 페이지의 뒤늦은 명령과 종료 이벤트는 현재 플레이어에게 영향을 주지 않는다. */
    public boolean ownsPage(String currentPageId) {
        return currentPageId != null && currentPageId.equals(pageId) && exitDeadline == null;
    }

    /** 중복 pagehide가 퇴장 마감을 계속 연장하지 못하게 최초 종료 시각을 유지한다. */
    public void closePage(Instant deadline) {
        if (exitDeadline == null || deadline.isBefore(exitDeadline)) exitDeadline = deadline;
    }

    /** 읽기 요청마다 UPDATE하지 않고 15초 이상 지난 접속 확인만 저장한다. */
    public void seen(Instant now) {
        if (lastSeenAt.isBefore(now.minusSeconds(15))) lastSeenAt = now;
    }
}

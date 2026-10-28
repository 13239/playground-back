package com.project.playground.domain.memory;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.Objects;

/** 방과 현재 판은 한 행 잠금으로 갱신한다. 과거 판의 명령은 matchId/revision으로 차단한다. */
@Entity @Table(name = "tbl_memory_room") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemoryRoom {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(length = 36, nullable = false, unique = true) private String requestId;
    @Column(length = 6, nullable = false, unique = true) private String code;
    @Column(length = 30, nullable = false) private String title;
    @Column(length = 255) private String passwordHash;
    private boolean solo;
    @Column(nullable = false) private Long hostId;
    private Long guestId;
    @Column(length = 36) private String hostLogin;
    @Column(length = 36) private String guestLogin;
    @Column(length = 36) private String hostTab;
    @Column(length = 36) private String guestTab;
    private Instant hostSeen;
    private Instant guestSeen;
    private boolean hostReady;
    private boolean guestReady;
    private boolean hostRematch;
    private boolean guestRematch;
    private boolean closed;
    private long revision;
    @Column(nullable = false) private Instant createdAt;
    @Embedded private MemoryBoard board = new MemoryBoard();

    @Builder
    public MemoryRoom(String requestId, String code, String title, String passwordHash, boolean solo,
                      Long hostId, String hostLogin, String hostTab, Instant now) {
        this.requestId = requestId; this.code = code; this.title = title; this.passwordHash = passwordHash;
        this.solo = solo; this.hostId = hostId; this.hostLogin = hostLogin; this.hostTab = hostTab;
        this.createdAt = now; this.hostSeen = now;
    }
    public int seat(Long userId) { return hostId.equals(userId) ? 1 : Objects.equals(guestId, userId) ? 2 : 0; }
    public String login(int seat) { return seat == 1 ? hostLogin : guestLogin; }
    public String tab(int seat) { return seat == 1 ? hostTab : guestTab; }
    public void touch(int seat, String login, String tab, Instant now) {
        if (seat == 1) { hostLogin = login; hostTab = tab; hostSeen = now; }
        else { guestLogin = login; guestTab = tab; guestSeen = now; }
    }
    public void join(Long userId, String login, String tab, Instant now) {
        guestId = userId; guestLogin = login; guestTab = tab; guestSeen = now;
        hostReady = false; guestReady = false; hostRematch = false; guestRematch = false; changed();
    }
    public void ready(int seat, boolean ready) { if (seat == 1) hostReady = ready; else guestReady = ready; changed(); }
    public void rematch(int seat) { if (seat == 1) hostRematch = true; else guestRematch = true; changed(); }
    public void begin(boolean rematch, Instant now) {
        board.begin(rematch, now); hostReady = false; guestReady = false; hostRematch = false; guestRematch = false; changed();
    }
    public void leave(int seat) {
        if (seat == 2 && "WAITING".equals(board.getPhase())) {
            guestId = null; guestLogin = null; guestTab = null; guestSeen = null; guestReady = false; hostReady = false;
        } else closed = true;
        changed();
    }
    public void close() { closed = true; changed(); }
    public void changed() { revision++; }
    /** 매초 DB를 순회하지 않도록 다음 게임·접속 마감 하나만 메모리 시계에 예약한다. */
    public Instant nextDeadline() {
        if (closed) return null;
        Instant next = hostSeen.plusSeconds(solo ? 7200 : 60);
        if (guestSeen != null && guestSeen.plusSeconds(60).isBefore(next)) next = guestSeen.plusSeconds(60);
        if (board.getDeadline() != null && board.getDeadline().isBefore(next)) next = board.getDeadline();
        return next;
    }
}

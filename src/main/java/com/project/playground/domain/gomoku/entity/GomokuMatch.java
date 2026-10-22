package com.project.playground.domain.gomoku.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 방 행 잠금을 공유하는 대국 원본이다. 클라이언트 시계·돌 색·승패·재시도 횟수를 신뢰하지 않는다. */
@Entity @Table(name = "tbl_gomoku_match") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GomokuMatch {
    public enum Phase { DRAWING, REVEAL, PLAYING, FINISHED }
    public enum FinishReason { FIVE, BOARD_FULL, RESIGN, DISCONNECT, INTERRUPTED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "room_id", nullable = false) private Long roomId;
    @Column(nullable = false) private int roundNumber;
    // 현재 참가 행이 퇴장으로 삭제돼도 과거 대국의 승자·패자를 회원에 연결할 수 있도록 원래 두 회원을 보존한다.
    @Column(name = "user_one_id", nullable = false) private Long userOneId;
    @Column(name = "user_two_id", nullable = false) private Long userTwoId;
    @Column(name = "membership_one", nullable = false, length = 36, columnDefinition = "char(36)") private String membershipOne;
    @Column(name = "membership_two", nullable = false, length = 36, columnDefinition = "char(36)") private String membershipTwo;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private Phase phase;
    private short blackSeat;
    private short blackCard;
    private short pickOne = -1;
    private short pickTwo = -1;
    private short turnSeat = 1;
    private long turnNumber;
    private Instant deadline;
    @Column(nullable = false) private Instant seenOne;
    @Column(nullable = false) private Instant seenTwo;
    private short undoOne;
    private short undoTwo;
    private short undoSeat;
    private Instant undoDeadline;
    private short winnerSeat;
    @Enumerated(EnumType.STRING) @Column(length = 16) private FinishReason finishReason;
    private boolean rematchOne;
    private boolean rematchTwo;
    @Column(length = 36) private String requestOne;
    @Column(length = 36) private String requestTwo;
    @Column(length = 100) private String commandOne;
    @Column(length = 100) private String commandTwo;
    @ElementCollection
    @CollectionTable(name = "tbl_gomoku_match_move", joinColumns = @JoinColumn(name = "match_id"))
    @OrderColumn(name = "ply")
    private List<GomokuMove> moves = new ArrayList<>();

    public static GomokuMatch begin(Long roomId, int round, Long userOne, Long userTwo, String one, String two, short assignedBlack, short blackCard, Instant now) {
        var match = new GomokuMatch();
        match.roomId = roomId; match.roundNumber = round;
        match.userOneId = userOne; match.userTwoId = userTwo;
        match.membershipOne = one; match.membershipTwo = two;
        match.blackCard = blackCard; match.blackSeat = assignedBlack;
        match.seenOne = now; match.seenTwo = now;
        match.phase = assignedBlack == 0 ? Phase.DRAWING : Phase.REVEAL;
        match.deadline = now.plusSeconds(assignedBlack == 0 ? 20 : 3);
        return match;
    }

    /** 카드 내용은 두 선택이 끝날 때까지 응답하지 않는다. 서버가 미리 정한 무작위 카드를 뒤집는다. */
    public void pick(short seat, short card, Instant now) {
        if (seat == 1) pickOne = card; else pickTwo = card;
        if (pickOne >= 0 && pickTwo >= 0) reveal(now);
    }
    public void reveal(Instant now) {
        if (pickOne < 0) pickOne = (short) (pickTwo == 0 ? 1 : 0);
        if (pickTwo < 0) pickTwo = (short) (1 - pickOne);
        blackSeat = (short) (pickOne == blackCard ? 1 : 2);
        phase = Phase.REVEAL; deadline = now.plusSeconds(3);
    }
    public void play(Instant now) { phase = Phase.PLAYING; turnSeat = blackSeat; deadline = now.plusSeconds(30); }
    public void seen(short seat, Instant now) { if (seat == 1) seenOne = now; else seenTwo = now; }
    public void move(short seat, short x, short y, Instant now) {
        moves.add(GomokuMove.builder().seat(seat).x(x).y(y).build());
        nextTurn(now);
    }
    /** 시간 초과는 패배가 아닌 패스다. 누락된 여러 턴도 기존 마감 시각을 기준으로 순서대로 넘긴다. */
    public void pass() { nextTurn(deadline); }
    private void nextTurn(Instant start) {
        turnSeat = (short) (3 - turnSeat); turnNumber++;
        deadline = start.plusSeconds(30); clearUndo();
    }
    public void finish(short winner, FinishReason reason) {
        phase = Phase.FINISHED; winnerSeat = winner; finishReason = reason; deadline = null; clearUndo();
    }
    public int lastMove(short seat) {
        for (int i = moves.size() - 1; i >= 0; i--) if (moves.get(i).getSeat() == seat) return i;
        return -1;
    }
    public int usedUndo(short seat) { return seat == 1 ? undoOne : undoTwo; }
    public void requestUndo(short seat, Instant now) { undoSeat = seat; undoDeadline = now.plusSeconds(10); }
    public void acceptUndo(Instant now) {
        short requester = undoSeat;
        int from = lastMove(requester);
        moves.subList(from, moves.size()).clear();
        if (requester == 1) undoOne++; else undoTwo++;
        turnSeat = requester; turnNumber++; deadline = now.plusSeconds(30); clearUndo();
    }
    public void clearUndo() { undoSeat = 0; undoDeadline = null; }
    /** 진행 중인 판의 가장 이른 서버 마감만 예약한다. 종료된 판은 더 이상 시각 확인이 필요 없다. */
    public Instant nextDeadline(int disconnectSeconds) {
        if (phase == Phase.FINISHED) return null;
        Instant next = seenOne.plusSeconds(disconnectSeconds);
        Instant other = seenTwo.plusSeconds(disconnectSeconds);
        if (other.isBefore(next)) next = other;
        if (deadline != null && deadline.isBefore(next)) next = deadline;
        if (undoDeadline != null && undoDeadline.isBefore(next)) next = undoDeadline;
        return next;
    }
    public void rematch(short seat) { if (seat == 1) rematchOne = true; else rematchTwo = true; }
    public boolean repeated(short seat, String request) { return request.equals(seat == 1 ? requestOne : requestTwo); }
    public String previousCommand(short seat) { return seat == 1 ? commandOne : commandTwo; }
    public void remember(short seat, String request, String command) {
        if (seat == 1) { requestOne = request; commandOne = command; }
        else { requestTwo = request; commandTwo = command; }
    }
}

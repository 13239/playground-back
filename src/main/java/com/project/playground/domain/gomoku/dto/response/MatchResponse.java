package com.project.playground.domain.gomoku.dto.response;

import com.project.playground.domain.gomoku.entity.GomokuMatch;
import lombok.Builder;
import lombok.Getter;
import java.time.Instant;
import java.util.List;

/** 추첨의 숨긴 카드와 내부 참가 식별자는 공개하지 않는다. 시각은 UTC 서버 시각과 함께 비교한다. */
@Getter @Builder
public class MatchResponse {
    private final Long id;
    private final int round;
    private final GomokuMatch.Phase phase;
    private final int blackSeat;
    private final int turnSeat;
    private final long turnNumber;
    private final Instant deadline;
    private final int pickOne;
    private final int pickTwo;
    private final int undoOne;
    private final int undoTwo;
    private final int undoSeat;
    private final Instant undoDeadline;
    private final int winnerSeat;
    private final GomokuMatch.FinishReason finishReason;
    private final boolean rematchOne;
    private final boolean rematchTwo;
    private final boolean connectedOne;
    private final boolean connectedTwo;
    private final List<Move> moves;

    public static MatchResponse from(GomokuMatch match, Instant now) {
        return MatchResponse.builder().id(match.getId()).round(match.getRoundNumber()).phase(match.getPhase())
                .blackSeat(match.getBlackSeat()).turnSeat(match.getTurnSeat()).turnNumber(match.getTurnNumber()).deadline(match.getDeadline())
                .pickOne(match.getPickOne()).pickTwo(match.getPickTwo())
                .undoOne(match.getUndoOne()).undoTwo(match.getUndoTwo()).undoSeat(match.getUndoSeat()).undoDeadline(match.getUndoDeadline())
                .winnerSeat(match.getWinnerSeat()).finishReason(match.getFinishReason())
                .rematchOne(match.isRematchOne()).rematchTwo(match.isRematchTwo())
                .connectedOne(!match.getSeenOne().isBefore(now.minusSeconds(15)))
                .connectedTwo(!match.getSeenTwo().isBefore(now.minusSeconds(15)))
                .moves(match.getMoves().stream().map(move -> Move.builder().x(move.getX()).y(move.getY()).seat(move.getSeat()).build()).toList()).build();
    }
    @Getter @Builder
    public static class Move { private final int x; private final int y; private final int seat; }
}

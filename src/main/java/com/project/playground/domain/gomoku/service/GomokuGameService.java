package com.project.playground.domain.gomoku.service;

import com.project.playground.domain.gomoku.dto.request.GameActionRequest;
import com.project.playground.domain.gomoku.dto.response.WaitingRoomResponse;
import com.project.playground.domain.gomoku.entity.*;
import com.project.playground.domain.gomoku.enums.RoomStatus;
import com.project.playground.domain.gomoku.repository.*;
import com.project.playground.domain.user.enums.UserStatus;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import com.project.playground.domain.game.service.GameAvailabilityService;
import com.project.playground.domain.gomoku.module.GomokuModule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** 대국 명령·시각 경과·재대국을 같은 방 잠금으로 직렬화한다. 게임 상태의 원본은 MySQL이다. */
@Service @RequiredArgsConstructor
@Transactional(timeout = 5, isolation = Isolation.READ_COMMITTED)
public class GomokuGameService {
    // 모바일 백그라운드 일시 정지를 고려해 접속 복구 시간을 150초 확보한다. 브라우저의 5회 재시도도 포함한다.
    // 브라우저의 5회 재시도(1·2·4·8·10초 간격, 요청당 5초)와 끊김 감지 시간을 포함한다.
    // 실패한 네트워크 요청은 서버에 도착하지 않으므로 닫힌 브라우저도 정리할 서버 마감이 별도로 필요하다.
    public static final int DISCONNECT_SECONDS = 150;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final GomokuRoomRepository rooms;
    private final GomokuRoomParticipantRepository participants;
    private final GomokuMatchRepository matches;
    private final RoomRateLimitService limits;
    private final RoomChatAccessPublisher chatAccess;
    private final GomokuGameEvents events;
    private final Clock gomokuClock;
    private final GameAvailabilityService availability;
    private final GomokuLobbyEvents lobbyEvents;
    private final GomokuPlayerStatsService playerStats;

    public void startIfReady(GomokuRoom room, List<GomokuRoomParticipant> members) {
        if (room.getStatus() == RoomStatus.WAITING && members.size() == 2 && members.stream().allMatch(GomokuRoomParticipant::isReady)) {
            begin(room, members, null, gomokuClock.instant());
        }
    }

    private GomokuMatch begin(GomokuRoom room, List<GomokuRoomParticipant> members, GomokuMatch previous, Instant now) {
        // 새 첫판/재대국에만 현재 정책을 확인한다. 기존 판의 착수·기권·마감 처리는 계속 허용한다.
        availability.requireNewPlay(GomokuModule.GAME, GomokuModule.ONLINE);
        var latest = matches.findFirstByRoomIdOrderByIdDesc(room.getId());
        short assigned = previous != null && previous.getWinnerSeat() > 0 ? (short) (3 - previous.getWinnerSeat()) : 0;
        var match = matches.saveAndFlush(GomokuMatch.begin(room.getId(), latest.map(m -> m.getRoundNumber() + 1).orElse(1),
                member(members, (short) 1).getUser().getId(), member(members, (short) 2).getUser().getId(),
                member(members, (short) 1).getMembershipId(), member(members, (short) 2).getMembershipId(),
                assigned, (short) RANDOM.nextInt(2), now));
        room.startPlaying();
        // 대국 착수·무르기마다 알리지 않고 목록의 대기/게임 상태가 바뀔 때만 알린다.
        lobbyEvents.changed();
        events.changed(room.getId());
        events.clockChanged(match);
        return match;
    }

    /** 참가자 조회는 접속 확인도 수행한다. 먼저 서버 마감을 적용해 지나치게 늦은 복귀가 패배를 취소하지 못하게 한다. */
    public GomokuMatch synchronize(GomokuRoom room, List<GomokuRoomParticipant> members, Long viewer) {
        if (room.getStatus() != RoomStatus.PLAYING) return null;
        var match = matches.findFirstByRoomIdOrderByIdDesc(room.getId()).orElseThrow(() -> failure(ErrorCode.GAME_STATE_CHANGED));
        if (members.size() != 2 || !member(members, (short) 1).getMembershipId().equals(match.getMembershipOne())
                || !member(members, (short) 2).getMembershipId().equals(match.getMembershipTwo())) throw failure(ErrorCode.GAME_STATE_CHANGED);
        Instant now = gomokuClock.instant();
        Instant previousDeadline = match.nextDeadline(DISCONNECT_SECONDS);
        boolean advanced = advance(match, now);
        if (advanced) touch(room);
        boolean seenUpdated = false;
        if (viewer != null && match.getPhase() != GomokuMatch.Phase.FINISHED) {
            short seat = own(members, viewer).getSeat();
            Instant seen = seat == 1 ? match.getSeenOne() : match.getSeenTwo();
            if (seen.isBefore(now.minusSeconds(3))) { match.seen(seat, now); seenUpdated = true; }
        }
        // 조회·접속 갱신도 커밋된 마감으로 예약한다. 판이 없는 동안의 DB 폴링은 하지 않는다.
        if (advanced || seenUpdated && !previousDeadline.equals(match.nextDeadline(DISCONNECT_SECONDS))) events.clockChanged(match);
        return match;
    }

    public WaitingRoomResponse action(Long userId, GameActionRequest request) {
        limits.game(userId);
        var room = rooms.findForUpdateById(request.getRoomId()).orElseThrow(() -> failure(ErrorCode.ROOM_NOT_FOUND));
        var members = participants.findMembers(room.getId());
        var own = own(members, userId);
        if (!own.ownsPage(request.getPageId())) throw failure(ErrorCode.ROOM_PAGE_IN_USE);
        short seat = own.getSeat();
        var match = synchronize(room, members, userId);
        if (match == null || !match.getId().equals(request.getMatchId())) throw failure(ErrorCode.GAME_STATE_CHANGED);
        String fingerprint = request.fingerprint();
        if (match.repeated(seat, request.getRequestId())) {
            if (!fingerprint.equals(match.previousCommand(seat))) throw failure(ErrorCode.GAME_STATE_CHANGED);
            return response(room, members, userId, match);
        }
        // 이미 완료된 대국에 대한 기권/단절과 같은 참가자의 중복 재대국 요청은 수정·이벤트 없이 성공한다.
        if ((match.getPhase() == GomokuMatch.Phase.FINISHED
                && (request.getAction() == GameActionRequest.Action.RESIGN || request.getAction() == GameActionRequest.Action.DISCONNECT))
                || (request.getAction() == GameActionRequest.Action.REMATCH && match.getPhase() == GomokuMatch.Phase.FINISHED
                    && (seat == 1 ? match.isRematchOne() : match.isRematchTwo()))) {
            return response(room, members, userId, match);
        }
        if (room.getRevision() != request.getRevision()) throw failure(ErrorCode.GAME_STATE_CHANGED);
        Instant now = gomokuClock.instant();
        switch (request.getAction()) {
            case PICK -> {
                requirePhase(match, GomokuMatch.Phase.DRAWING);
                int ownPick = seat == 1 ? match.getPickOne() : match.getPickTwo();
                int otherPick = seat == 1 ? match.getPickTwo() : match.getPickOne();
                if (ownPick >= 0 || otherPick == request.getCard()) throw failure(ErrorCode.GAME_STATE_CHANGED);
                match.pick(seat, request.getCard(), now);
            }
            case MOVE -> {
                requirePhase(match, GomokuMatch.Phase.PLAYING);
                // 상대 브라우저가 무르기 팝업을 아직 받지 못한 순간에도 착수로 요청을 몰래 소멸시키지 않는다.
                if (match.getUndoSeat() != 0) throw failure(ErrorCode.GAME_UNDO_UNAVAILABLE);
                if (match.getTurnSeat() != seat) throw failure(ErrorCode.GAME_NOT_YOUR_TURN);
                var result = GomokuRules.evaluate(match.getMoves(), match.getBlackSeat(), seat, request.getX(), request.getY(), room.getForbiddenRule());
                if (result == GomokuRules.Result.OCCUPIED) throw failure(ErrorCode.GAME_POSITION_OCCUPIED);
                if (result == GomokuRules.Result.FORBIDDEN) throw failure(ErrorCode.GAME_FORBIDDEN_MOVE);
                match.move(seat, request.getX(), request.getY(), now);
                if (result == GomokuRules.Result.WIN) finishWithStats(match, seat, GomokuMatch.FinishReason.FIVE);
                else if (match.getMoves().size() == 400) finishWithStats(match, (short) 0, GomokuMatch.FinishReason.BOARD_FULL);
            }
            case UNDO_REQUEST -> {
                requirePhase(match, GomokuMatch.Phase.PLAYING);
                if (match.getUndoSeat() != 0 || match.lastMove(seat) < 0 || match.usedUndo(seat) >= room.getUndoLimit()) {
                    throw failure(ErrorCode.GAME_UNDO_UNAVAILABLE);
                }
                match.requestUndo(seat, now);
            }
            case UNDO_REPLY -> {
                requirePhase(match, GomokuMatch.Phase.PLAYING);
                if (match.getUndoSeat() == 0 || match.getUndoSeat() == seat) throw failure(ErrorCode.GAME_UNDO_UNAVAILABLE);
                if (request.getAccept()) match.acceptUndo(now); else match.clearUndo();
            }
            case RESIGN, DISCONNECT -> {
                if (match.getPhase() != GomokuMatch.Phase.FINISHED) finishWithStats(match, (short) (3 - seat),
                        request.getAction() == GameActionRequest.Action.RESIGN ? GomokuMatch.FinishReason.RESIGN : GomokuMatch.FinishReason.DISCONNECT);
            }
            case REMATCH -> {
                requirePhase(match, GomokuMatch.Phase.FINISHED);
                // 첫 사람의 재대국 요청부터 거절해 점검 중 대기 상태만 남지 않게 한다.
                availability.requireNewPlay(GomokuModule.GAME, GomokuModule.ONLINE);
                match.rematch(seat);
                if (match.isRematchOne() && match.isRematchTwo()) match = begin(room, members, match, now);
            }
        }
        match.remember(seat, request.getRequestId(), fingerprint);
        touch(room);
        events.clockChanged(match);
        members.forEach(p -> { if (p.getUser().getId().equals(userId)) p.seen(now); });
        return snapshot(room, members, userId, match);
    }

    /** 명시적 나가기는 기권으로 기록한 뒤 대기실로 되돌린다. 남은 사용자는 새 상대와 다시 추첨한다. */
    public void departed(GomokuRoom room, List<GomokuRoomParticipant> members, Long userId) {
        if (room.getStatus() != RoomStatus.PLAYING) return;
        var match = synchronize(room, members, null);
        if (match.getPhase() != GomokuMatch.Phase.FINISHED) finishWithStats(match, (short) (3 - own(members, userId).getSeat()), GomokuMatch.FinishReason.RESIGN);
        // 퇴장에 따른 로비 변경 알림은 호출자의 참가자 제거 처리에서 한 번만 발행한다.
        room.returnToWaiting(); touch(room);
        events.clockChanged(match);
    }

    /** 마감에 도달한 방만 잠그고 최신 상태를 확인한 뒤 다음 확인 시각을 반환한다. */
    public Instant tick(Long roomId) {
        var room = rooms.findForUpdateById(roomId).orElse(null);
        return room == null ? null : tickLocked(room);
    }

    /** 같은 서비스 패키지에서 이미 확보한 방 잠금을 재사용해 종료 예약 확인 뒤 방을 중복 조회하지 않는다. */
    Instant tickLocked(GomokuRoom room) {
        if (room.getStatus() != RoomStatus.PLAYING) return null;
        var match = matches.findFirstByRoomIdOrderByIdDesc(room.getId()).orElse(null);
        if (match != null && advance(match, gomokuClock.instant())) touch(room);
        return match == null ? null : match.nextDeadline(DISCONNECT_SECONDS);
    }

    private boolean advance(GomokuMatch match, Instant now) {
        if (match.getPhase() == GomokuMatch.Phase.FINISHED) return false;
        boolean oneGone = !match.getSeenOne().plusSeconds(DISCONNECT_SECONDS).isAfter(now);
        boolean twoGone = !match.getSeenTwo().plusSeconds(DISCONNECT_SECONDS).isAfter(now);
        if (oneGone || twoGone) {
            // 양쪽 동시 단절이나 긴 서버 정지는 특정 사용자 패배로 만들지 않는다.
            finishWithStats(match, (short) (oneGone && twoGone ? 0 : oneGone ? 2 : 1), oneGone && twoGone
                    ? GomokuMatch.FinishReason.INTERRUPTED : GomokuMatch.FinishReason.DISCONNECT);
            return true;
        }
        boolean changed = false;
        if (match.getPhase() == GomokuMatch.Phase.DRAWING && !match.getDeadline().isAfter(now)) { match.reveal(now); changed = true; }
        if (match.getPhase() == GomokuMatch.Phase.REVEAL && !match.getDeadline().isAfter(now)) { match.play(now); changed = true; }
        // 긴 중단에서도 무한 반복하지 않고 경과한 패스 횟수를 최대 3회 처리한다. 그 전에 접속 만료가 판정된다.
        while (match.getPhase() == GomokuMatch.Phase.PLAYING && !match.getDeadline().isAfter(now)) { match.pass(); changed = true; }
        if (match.getUndoDeadline() != null && !match.getUndoDeadline().isAfter(now)) { match.clearUndo(); changed = true; }
        return changed;
    }

    /** 방 잠금을 확보한 서비스의 종료 경로를 통합해 승·패·무를 한 트랜잭션에서 한 번만 기록한다. */
    private void finishWithStats(GomokuMatch match, short winner, GomokuMatch.FinishReason reason) {
        if (match.getPhase() == GomokuMatch.Phase.FINISHED) return;
        match.finish(winner, reason);
        playerStats.recordFinished(match);
    }

    private WaitingRoomResponse snapshot(GomokuRoom room, List<GomokuRoomParticipant> members, Long viewer, GomokuMatch match) {
        chatAccess.changed(room, members);
        return response(room, members, viewer, match);
    }

    /** 중복 명령 응답은 채팅 리스·이벤트를 재발행하지 않고 공개 전적만 조회한다. */
    private WaitingRoomResponse response(GomokuRoom room, List<GomokuRoomParticipant> members, Long viewer, GomokuMatch match) {
        return WaitingRoomResponse.from(room, members, viewer, match, gomokuClock.instant(), playerStats.forMembers(members));
    }
    private void touch(GomokuRoom room) { room.participantsChanged(room.getPlayerCount()); events.changed(room.getId()); }
    private void requirePhase(GomokuMatch match, GomokuMatch.Phase phase) { if (match.getPhase() != phase) throw failure(ErrorCode.GAME_STATE_CHANGED); }
    private GomokuRoomParticipant own(List<GomokuRoomParticipant> members, Long userId) {
        return members.stream().filter(p -> p.getUser().getId().equals(userId)
                && p.getUser().getStatus() == UserStatus.ACTIVE && p.getUser().getDeletedAt() == null)
                .findFirst().orElseThrow(() -> failure(ErrorCode.ROOM_NOT_JOINED));
    }
    private GomokuRoomParticipant member(List<GomokuRoomParticipant> members, short seat) {
        return members.stream().filter(p -> p.getSeat() == seat).findFirst().orElseThrow(() -> failure(ErrorCode.ROOM_NOT_FULL));
    }
    private BusinessException failure(ErrorCode code) { return new BusinessException(code); }
}

package com.project.playground.domain.gomoku.service;

import com.project.playground.domain.gomoku.dto.request.JoinRoomRequest;
import com.project.playground.domain.gomoku.dto.request.RoomPageRequest;
import com.project.playground.domain.gomoku.dto.response.WaitingRoomResponse;
import com.project.playground.domain.gomoku.entity.GomokuRoom;
import com.project.playground.domain.gomoku.entity.GomokuRoomParticipant;
import com.project.playground.domain.gomoku.enums.RoomStatus;
import com.project.playground.domain.gomoku.enums.RoomVisibility;
import com.project.playground.domain.gomoku.repository.GomokuRoomParticipantRepository;
import com.project.playground.domain.gomoku.repository.GomokuRoomRepository;
import com.project.playground.domain.user.entity.User;
import com.project.playground.domain.user.enums.UserStatus;
import com.project.playground.domain.user.repository.UserRepository;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/** 방 행 잠금으로 동시 입장·준비·퇴장을 직렬화하고 두 명의 준비 완료를 서버 추첨으로 연결한다. */
@Service @RequiredArgsConstructor
// 회원·방 잠금을 기다린 뒤에는 직전 커밋의 참가자를 읽어 MySQL의 이전 스냅샷으로 빈 좌석을 오인하지 않는다.
@Transactional(timeout = 5, isolation = Isolation.READ_COMMITTED)
public class GomokuWaitingRoomService {
    // 새로고침이 참가를 이어받을 짧은 유예다. 일반 뒤로가기·나가기에는 적용하지 않는다.
    private static final int PAGE_CLOSE_GRACE_SECONDS = 5;
    private final GomokuRoomRepository rooms;
    private final GomokuRoomParticipantRepository participants;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final RoomRateLimitService limits;
    private final RoomChatAccessPublisher chatAccess;
    private final GomokuGameService games;
    private final GomokuGameEvents events;
    private final Clock gomokuClock;

    public WaitingRoomResponse join(Long userId, JoinRoomRequest request) {
        limits.join(userId);
        User user = activeUser(userId);
        Long roomId = request.getRoomId() != null ? request.getRoomId()
                : rooms.findByInviteCode(request.getInviteCode()).map(GomokuRoom::getId)
                    .orElseThrow(() -> failure(ErrorCode.ROOM_NOT_FOUND));
        // 회원 확인 → 방 잠금 순서를 유지한다. 계정별 중복 참가는 DB 고유 제약이 최종 보장한다.
        var current = participants.findCurrent(userId);
        if (current.isEmpty()) {
            // 이미 참가한 계정의 복귀에는 회원 행 잠금이 필요 없다. 신규 입장만 생성과 같은 계정 잠금을 공유한다.
            user = users.findForUpdateById(userId).filter(u -> u.getStatus() == UserStatus.ACTIVE && u.getDeletedAt() == null)
                    .orElseThrow(() -> failure(ErrorCode.USER_NOT_FOUND));
            current = participants.findCurrent(userId);
        }
        if (current.isPresent() && !current.get().getRoom().getId().equals(roomId)) {
            throw failure(ErrorCode.ROOM_ALREADY_JOINED);
        }
        GomokuRoom room = openRoom(roomId);
        var members = new ArrayList<>(participants.findMembers(roomId));
        var existing = members.stream().filter(p -> p.getUser().getId().equals(userId)).findFirst();
        if (existing.isPresent()) {
            existing.get().seen(Instant.now());
            if (room.getStatus() == RoomStatus.WAITING) cleanExpired(room, members, userId);
            return snapshot(room, members, userId);
        }
        // 이미 참가한 사용자는 진행 중인 판으로 복귀하지만 신규 사용자는 대국에 끼어들 수 없다.
        if (room.getStatus() != RoomStatus.WAITING) throw failure(ErrorCode.ROOM_FULL);
        cleanExpired(room, members, null);
        if (room.getStatus() == RoomStatus.CLOSED) throw failure(ErrorCode.ROOM_CLOSED);
        // 코드만으로 비공개 방의 비밀번호 검증을 건너뛰지 않는다.
        if (room.getVisibility() == RoomVisibility.PRIVATE) {
            if (request.getPassword() == null || request.getPassword().isEmpty()) throw failure(ErrorCode.ROOM_PASSWORD_REQUIRED);
            if (!passwords.matches(request.getPassword(), room.getPasswordHash())) throw failure(ErrorCode.ROOM_PASSWORD_INVALID);
        }
        if (members.size() >= 2) throw failure(ErrorCode.ROOM_FULL);
        short seat = (short) (members.stream().anyMatch(p -> p.getSeat() == 1) ? 2 : 1);
        // 삭제와 새 좌석 INSERT 순서를 고정해 만료 참가자의 고유 제약이 새 입장을 막지 않게 한다.
        participants.flush();
        var member = participants.save(GomokuRoomParticipant.builder().room(room).user(user).seat(seat)
                .joinedAtMicros(chatAccess.nowMicros()).build());
        members.add(member);
        members.forEach(p -> p.ready(false));
        room.participantsChanged(members.size());
        return snapshot(room, members, userId);
    }

    public WaitingRoomResponse state(Long userId, Long roomId) {
        limits.state(userId);
        GomokuRoom room = openRoom(roomId);
        var members = new ArrayList<>(participants.findMembers(roomId));
        var own = requireMember(members, userId);
        Instant now = Instant.now();
        // 오래 멈춘 탭의 준비 상태는 자동 유지하지 않는다. 새로고침은 참가 상태를 복구한다.
        if (room.getStatus() == RoomStatus.WAITING && own.isReady() && own.getLastSeenAt().isBefore(now.minusSeconds(30))) {
            own.ready(false);
            room.participantsChanged(members.size());
        }
        own.seen(now);
        if (room.getStatus() == RoomStatus.WAITING) cleanExpired(room, members, userId);
        return snapshot(room, members, userId);
    }

    public WaitingRoomResponse ready(Long userId, Long roomId, boolean ready) {
        limits.ready(userId);
        activeUser(userId);
        GomokuRoom room = openRoom(roomId);
        if (room.getStatus() != RoomStatus.WAITING) throw failure(ErrorCode.GAME_STATE_CHANGED);
        var members = new ArrayList<>(participants.findMembers(roomId));
        var own = requireMember(members, userId);
        own.seen(Instant.now());
        cleanExpired(room, members, userId);
        if (ready && members.size() != 2) throw failure(ErrorCode.ROOM_NOT_FULL);
        if (own.isReady() != ready) {
            own.ready(ready);
            room.participantsChanged(members.size());
        }
        games.startIfReady(room, members);
        events.changed(roomId);
        return snapshot(room, members, userId);
    }

    public void leave(Long userId, Long roomId, String membershipId) {
        limits.leave(userId);
        activeUser(userId);
        var found = rooms.findForUpdateById(roomId);
        if (found.isEmpty() || found.get().getStatus() == RoomStatus.CLOSED) return;
        GomokuRoom room = found.get();
        var members = new ArrayList<>(participants.findMembers(roomId));
        var own = members.stream().filter(p -> p.getUser().getId().equals(userId)).findFirst();
        if (own.isEmpty() || !own.get().getMembershipId().equals(membershipId)) return; // 네트워크 재시도의 같은 퇴장은 성공으로 처리한다.
        removeMember(room, members, own.get());
    }

    /** CLOSE는 짧게 예약하고 새 문서의 OPEN이 예약을 취소한다. 이전 문서·참가의 늦은 요청은 무시한다. */
    public void page(Long userId, RoomPageRequest request) {
        limits.lifecycle(userId);
        activeUser(userId);
        var found = rooms.findForUpdateById(request.getRoomId());
        if (found.isEmpty() || found.get().getStatus() == RoomStatus.CLOSED) return;
        var room = found.get();
        var members = new ArrayList<>(participants.findMembers(room.getId()));
        var own = members.stream().filter(p -> p.getUser().getId().equals(userId)
                && p.getMembershipId().equals(request.getMembershipId())).findFirst();
        if (own.isEmpty()) return;
        var participant = own.get();
        if (request.getAction() == RoomPageRequest.Action.OPEN) {
            participant.openPage(request.getPageId());
        } else if (request.getPageId().equals(participant.getPageId())) {
            if (request.getAction() == RoomPageRequest.Action.LEAVE) removeMember(room, members, participant);
            else {
                participant.closePage(gomokuClock.instant().plusSeconds(PAGE_CLOSE_GRACE_SECONDS));
                events.clockChanged(room.getId(), participant.getExitDeadline());
            }
        }
    }

    /** 메모리에 예약된 방만 확인한다. 새로고침으로 취소된 예약은 제거하고 대국의 다음 마감과 합친다. */
    public Instant tick(Long roomId) {
        var found = rooms.findForUpdateById(roomId);
        if (found.isEmpty() || found.get().getStatus() == RoomStatus.CLOSED) return null;
        var room = found.get();
        var members = new ArrayList<>(participants.findMembers(roomId));
        Instant now = gomokuClock.instant();
        var departing = members.stream().filter(p -> p.getExitDeadline() != null && !p.getExitDeadline().isAfter(now)).toList();
        departing.forEach(p -> removeMember(room, members, p));
        Instant next = room.getStatus() == RoomStatus.PLAYING ? games.tickLocked(room) : null;
        for (var participant : members) {
            Instant deadline = participant.getExitDeadline();
            if (deadline != null && (next == null || deadline.isBefore(next))) next = deadline;
        }
        return next;
    }

    private void removeMember(GomokuRoom room, List<GomokuRoomParticipant> members, GomokuRoomParticipant own) {
        games.departed(room, members, own.getUser().getId());
        participants.delete(own);
        members.remove(own);
        membershipChanged(room, members);
        chatAccess.changed(room, members);
        events.changed(room.getId());
    }

    /** 아무도 다시 조회하지 않는 방도 정리한다. 후보 조회 뒤 복귀한 참가자는 최신 시각으로 보존한다. */
    public void expire(Long roomId) {
        var found = rooms.findForUpdateById(roomId);
        if (found.isEmpty() || found.get().getStatus() == RoomStatus.CLOSED) return;
        var room = found.get();
        var members = new ArrayList<>(participants.findMembers(roomId));
        // 종료 요청 자체가 유실된 브라우저도 접속 만료 뒤 참가를 제거한다. 일시적인 대국 단절의 재접속 유예는 유지한다.
        if (room.getStatus() == RoomStatus.PLAYING) {
            var expired = members.stream().filter(p -> p.getLastSeenAt().isBefore(gomokuClock.instant().minusSeconds(90))).toList();
            expired.forEach(p -> removeMember(room, members, p));
            return;
        }
        long previous = room.getRevision();
        cleanExpired(room, members, null);
        if (room.getRevision() != previous) chatAccess.changed(room, members);
    }

    private GomokuRoom openRoom(Long id) {
        var room = rooms.findForUpdateById(id).orElseThrow(() -> failure(ErrorCode.ROOM_NOT_FOUND));
        if (room.getStatus() == RoomStatus.CLOSED) throw failure(ErrorCode.ROOM_CLOSED);
        return room;
    }

    private User activeUser(Long id) {
        return users.findById(id).filter(u -> u.getStatus() == UserStatus.ACTIVE && u.getDeletedAt() == null)
                .orElseThrow(() -> failure(ErrorCode.USER_NOT_FOUND));
    }

    private GomokuRoomParticipant requireMember(List<GomokuRoomParticipant> members, Long id) {
        return members.stream().filter(p -> p.getUser().getId().equals(id)
                && p.getUser().getStatus() == UserStatus.ACTIVE && p.getUser().getDeletedAt() == null)
                .findFirst().orElseThrow(() -> failure(ErrorCode.ROOM_NOT_JOINED));
    }

    /** 강제 종료·오래 끊긴 접속은 다른 참가자가 조회할 때 정리한다. 브라우저 닫기 이벤트에 퇴장을 의존하지 않는다. */
    private void cleanExpired(GomokuRoom room, List<GomokuRoomParticipant> members, Long requester) {
        Instant cutoff = Instant.now().minusSeconds(90);
        var stale = members.stream().filter(p -> !p.getUser().getId().equals(requester)
                && (p.getLastSeenAt().isBefore(cutoff) || p.getUser().getStatus() != UserStatus.ACTIVE
                    || p.getUser().getDeletedAt() != null)).toList();
        if (stale.isEmpty()) return;
        participants.deleteAll(stale);
        members.removeAll(stale);
        membershipChanged(room, members);
    }

    private void membershipChanged(GomokuRoom room, List<GomokuRoomParticipant> members) {
        members.forEach(p -> p.ready(false));
        if (members.isEmpty()) room.close();
        else {
            if (members.stream().noneMatch(p -> p.getUser().getId().equals(room.getHost().getId()))) {
                room.transferHost(members.getFirst().getUser());
            }
            room.participantsChanged(members.size());
        }
    }

    private WaitingRoomResponse snapshot(GomokuRoom room, List<GomokuRoomParticipant> members, Long viewer) {
        // 진행 중에도 참가 조회로 접속과 방 채팅 리스를 함께 유지하며 숨긴 추첨 결과는 공개하지 않는다.
        var game = games.synchronize(room, members, viewer);
        chatAccess.changed(room, members);
        return WaitingRoomResponse.from(room, members, viewer, game, Instant.now());
    }

    private BusinessException failure(ErrorCode code) { return new BusinessException(code); }
}

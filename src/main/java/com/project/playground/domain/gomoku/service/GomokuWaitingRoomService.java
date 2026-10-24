package com.project.playground.domain.gomoku.service;

import com.project.playground.auth.repository.RedisSessionRepository;
import com.project.playground.domain.gomoku.dto.request.JoinRoomRequest;
import com.project.playground.domain.gomoku.dto.request.KickRoomRequest;
import com.project.playground.domain.gomoku.dto.request.RoomPageRequest;
import com.project.playground.domain.gomoku.dto.response.WaitingRoomResponse;
import com.project.playground.domain.gomoku.entity.GomokuRoom;
import com.project.playground.domain.gomoku.entity.GomokuRoomKick;
import com.project.playground.domain.gomoku.entity.GomokuRoomParticipant;
import com.project.playground.domain.gomoku.enums.RoomStatus;
import com.project.playground.domain.gomoku.enums.RoomVisibility;
import com.project.playground.domain.gomoku.repository.GomokuRoomParticipantRepository;
import com.project.playground.domain.gomoku.repository.GomokuRoomKickRepository;
import com.project.playground.domain.gomoku.repository.GomokuRoomRepository;
import com.project.playground.domain.user.entity.User;
import com.project.playground.domain.user.enums.UserStatus;
import com.project.playground.domain.user.repository.UserRepository;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import com.project.playground.domain.game.service.GameAvailabilityService;
import com.project.playground.domain.gomoku.module.GomokuModule;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 방 행 잠금으로 동시 입장·준비·퇴장을 직렬화하고 두 명의 준비 완료를 서버 추첨으로 연결한다. */
@Service @RequiredArgsConstructor
// 회원·방 잠금을 기다린 뒤에는 직전 커밋의 참가자를 읽어 MySQL의 이전 스냅샷으로 빈 좌석을 오인하지 않는다.
@Transactional(timeout = 5, isolation = Isolation.READ_COMMITTED)
public class GomokuWaitingRoomService {
    // 새로고침이 참가를 이어받을 짧은 유예다. 일반 뒤로가기·나가기에는 적용하지 않는다.
    private static final int PAGE_CLOSE_GRACE_SECONDS = 5;
    private final GomokuRoomRepository rooms;
    private final GomokuRoomParticipantRepository participants;
    private final GomokuRoomKickRepository kicks;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final RoomRateLimitService limits;
    private final RoomChatAccessPublisher chatAccess;
    private final GomokuGameService games;
    private final GomokuGameEvents events;
    private final Clock gomokuClock;
    private final RedisSessionRepository sessions;
    private final GameAvailabilityService availability;
    private final GomokuLobbyEvents lobbyEvents;
    private final GomokuPlayerStatsService playerStats;

    // 만료된 마지막 참가자를 정리한 뒤 ROOM_CLOSED를 반환해도 실제 정리 트랜잭션은 커밋해야 한다.
    // 나머지 조회·입장 충돌은 변경 전 실패하므로 예외를 이유로 만료 정리를 되돌리지 않는다.
    @Transactional(timeout = 5, isolation = Isolation.READ_COMMITTED, noRollbackFor = BusinessException.class)
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
        // 방 행 잠금 안에서 이전 강퇴 정책을 읽어 영구 차단과 재참가 허용을 구분한다.
        var previousKick = kicks.findByRoomIdAndUserId(roomId, userId);
        if (previousKick.isPresent() && previousKick.get().isReentryBlocked()) throw failure(ErrorCode.ROOM_KICKED);
        var members = new ArrayList<>(participants.findMembers(roomId));
        var existing = members.stream().filter(p -> p.getUser().getId().equals(userId)).findFirst();
        if (existing.isPresent()) {
            // 이미 참가한 계정의 재입장은 읽기 전용이다. 새 탭이 정상 플레이어의 접속 시간을 연장하지 못한다.
            if (room.getStatus() == RoomStatus.WAITING) cleanExpired(room, members, null);
            return snapshot(room, members, userId, false);
        }
        // 이미 참가한 사용자는 진행 중인 판으로 복귀하지만 신규 사용자는 대국에 끼어들 수 없다.
        if (room.getStatus() != RoomStatus.WAITING) throw failure(ErrorCode.ROOM_FULL);
        // 기존 참가자의 진행 중 판 복귀는 유지하고 신규 참가만 숨김·점검 정책으로 차단한다.
        availability.requireNewPlay(GomokuModule.GAME, GomokuModule.ONLINE);
        cleanExpired(room, members, null);
        if (room.getStatus() == RoomStatus.CLOSED) throw failure(ErrorCode.ROOM_CLOSED);
        // 코드만으로 비공개 방의 비밀번호 검증을 건너뛰지 않는다.
        if (room.getVisibility() == RoomVisibility.PRIVATE) {
            if (request.getPassword() == null || request.getPassword().isEmpty()) throw failure(ErrorCode.ROOM_PASSWORD_REQUIRED);
            if (!passwords.matches(request.getPassword(), room.getPasswordHash())) throw failure(ErrorCode.ROOM_PASSWORD_INVALID);
        }
        if (members.size() >= 2) throw failure(ErrorCode.ROOM_FULL);
        short seat = (short) (members.stream().anyMatch(p -> p.getSeat() == 1) ? 2 : 1);
        // 재참가 허용 강퇴 기록은 새로운 유효 입장에 한해서 지운다. 이후 강퇴 시 고유 제약과 충돌하지 않는다.
        previousKick.ifPresent(kicks::delete);
        // 삭제와 새 좌석 INSERT 순서를 고정해 만료 참가자의 고유 제약이 새 입장을 막지 않게 한다.
        participants.flush();
        var member = participants.save(GomokuRoomParticipant.builder().room(room).user(user).seat(seat)
                .joinedAtMicros(chatAccess.nowMicros()).build());
        members.add(member);
        members.forEach(p -> p.ready(false));
        room.participantsChanged(members.size());
        // 정원 변경만 로비에 전달하고 접속 시간·준비 토글로 목록 조회를 발생시키지 않는다.
        lobbyEvents.changed();
        return snapshot(room, members, userId);
    }

    public WaitingRoomResponse state(Long userId, Long roomId, String pageId) {
        limits.state(userId);
        GomokuRoom room = openRoom(roomId);
        var members = new ArrayList<>(participants.findMembers(roomId));
        // 재입장 가능 여부에 따라 다른 안내를 전달하되 참가가 해제된 기존 페이지는 모두 종료한다.
        kicks.findByRoomIdAndUserId(roomId, userId).ifPresent(kick -> {
            throw failure(kick.isReentryBlocked() ? ErrorCode.ROOM_KICKED : ErrorCode.ROOM_KICKED_REJOIN_ALLOWED);
        });
        var own = requireMember(members, userId);
        // pageId 없는 첫 조회는 참가 식별자를 찾기 위한 읽기 전용 요청이다.
        // 페이지를 소유하지 않은 탭은 대국 연결 만료와 준비 상태를 변경하거나 SSE를 이용할 수 없다.
        boolean owner = own.ownsPage(pageId);
        if (pageId != null && !owner) throw failure(ErrorCode.ROOM_PAGE_IN_USE);
        Instant now = gomokuClock.instant();
        long previousRevision = room.getRevision();
        // 채팅 참가 리스는 25초이며, 상태를 5~15초마다 읽어도 Redis 재발행은 최대 15초에 한 번이다.
        boolean refreshAccess = owner && own.getLastSeenAt().isBefore(now.minusSeconds(15));
        if (owner) {
            // 오래 멈춘 탭의 준비 상태는 자동 유지하지 않는다. 새로고침은 참가 상태를 복구한다.
            if (room.getStatus() == RoomStatus.WAITING && own.isReady() && own.getLastSeenAt().isBefore(now.minusSeconds(30))) {
                own.ready(false);
                room.participantsChanged(members.size());
            }
            own.seen(now);
        }
        // 읽기 전용 탭은 참가자를 만료시키는 상태 변경도 수행하지 않는다.
        if (owner && room.getStatus() == RoomStatus.WAITING) cleanExpired(room, members, userId);
        return snapshot(room, members, userId, owner, refreshAccess || room.getRevision() != previousRevision);
    }

    public WaitingRoomResponse ready(Long userId, Long roomId, boolean ready, String pageId) {
        limits.ready(userId);
        activeUser(userId);
        GomokuRoom room = openRoom(roomId);
        if (room.getStatus() != RoomStatus.WAITING) throw failure(ErrorCode.GAME_STATE_CHANGED);
        var members = new ArrayList<>(participants.findMembers(roomId));
        var own = requireMember(members, userId);
        if (!own.ownsPage(pageId)) throw failure(ErrorCode.ROOM_PAGE_IN_USE);
        own.seen(Instant.now());
        cleanExpired(room, members, userId);
        if (ready && members.size() != 2) throw failure(ErrorCode.ROOM_NOT_FULL);
        boolean previousReady = own.isReady();
        if (previousReady != ready) {
            own.ready(ready);
            room.participantsChanged(members.size());
        }
        games.startIfReady(room, members);
        // 준비 상태가 그대로라면 SSE 알림과 Redis 발행을 반복하지 않는다. 대국 시작은 게임 서비스가 발행한다.
        if (room.getStatus() == RoomStatus.WAITING && previousReady != own.isReady()) events.changed(roomId);
        return snapshot(room, members, userId);
    }

    /** 방장 권한·점유 페이지·스냅샷 revision·목표 좌석을 잠금 안에서 확인한다. */
    public WaitingRoomResponse kick(Long userId, KickRoomRequest request) {
        limits.kick(userId);
        activeUser(userId);
        var room = openRoom(request.getRoomId());
        if (room.getStatus() != RoomStatus.WAITING || !room.getHost().getId().equals(userId)) {
            throw failure(ErrorCode.ROOM_KICK_FORBIDDEN);
        }
        var members = new ArrayList<>(participants.findMembers(room.getId()));
        var host = requireMember(members, userId);
        if (!host.ownsPage(request.getPageId())) throw failure(ErrorCode.ROOM_PAGE_IN_USE);
        // 방 상태가 변했으면 같은 좌석에 새로 입장한 다른 참가자를 잘못 강퇴하지 않는다.
        if (room.getRevision() != request.getRevision()) throw failure(ErrorCode.ROOM_KICK_TARGET_CHANGED);
        var target = members.stream().filter(p -> p.getSeat() == request.getSeat() && !p.getUser().getId().equals(userId))
                .findFirst().orElseThrow(() -> failure(ErrorCode.ROOM_KICK_TARGET_CHANGED));
        // 강퇴된 페이지에는 즉시 이탈을 알리고, 방장이 선택한 재참가 정책만 이후 입장에 적용한다.
        kicks.save(GomokuRoomKick.builder().room(room).userId(target.getUser().getId())
                .reentryBlocked(request.getBlockRejoin()).build());
        // 참가 제거·채팅 권한 회수·참가 순서 변경 및 이벤트 발행은 기존 퇴장 처리와 같다.
        removeMember(room, members, target);
        return snapshot(room, members, userId);
    }

    public void leave(Long userId, Long roomId, String membershipId, String pageId) {
        limits.leave(userId);
        activeUser(userId);
        var found = rooms.findForUpdateById(roomId);
        if (found.isEmpty() || found.get().getStatus() == RoomStatus.CLOSED) return;
        GomokuRoom room = found.get();
        var members = new ArrayList<>(participants.findMembers(roomId));
        var own = members.stream().filter(p -> p.getUser().getId().equals(userId)).findFirst();
        if (own.isEmpty() || !own.get().getMembershipId().equals(membershipId)) return; // 네트워크 재시도의 같은 퇴장은 성공으로 처리한다.
        if (own.get().getPageId() != null && !own.get().ownsPage(pageId)) throw failure(ErrorCode.ROOM_PAGE_IN_USE);
        removeMember(room, members, own.get());
    }

    /** CLOSE는 짧게 예약하고 새 문서의 OPEN이 예약을 취소한다. 이전 문서·참가의 늦은 요청은 무시한다. */
    public void page(Long userId, UUID loginSessionId, RoomPageRequest request) {
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
            // 방 잠금을 얻은 뒤 재검증해 인증 필터를 먼저 통과한 이전 로그인의 지연 OPEN을 차단한다.
            // 클라이언트가 보낸 탭 식별자가 아니라 인증 주체의 세션과 서버의 생성 시각만 신뢰한다.
            var login = sessions.findBySessionId(loginSessionId, userId)
                    .orElseThrow(() -> failure(ErrorCode.INVALID_SESSION));
            String previousLogin = participant.getLoginSessionId();
            if (previousLogin != null && !previousLogin.equals(loginSessionId.toString())
                    && sessions.findBySessionId(UUID.fromString(previousLogin), userId).isPresent()) {
                throw failure(ErrorCode.ROOM_PAGE_IN_USE);
            }
            if (!participant.openPage(loginSessionId.toString(), login.getCreatedAt(), request.getTabId(),
                    request.getPageId(), gomokuClock.instant())) {
                throw failure(ErrorCode.ROOM_PAGE_IN_USE);
            }
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
        lobbyEvents.changed();
    }

    /** 아무도 다시 조회하지 않는 방도 정리한다. 후보 조회 뒤 복귀한 참가자는 최신 시각으로 보존한다. */
    public void expire(Long roomId) {
        var found = rooms.findForUpdateById(roomId);
        if (found.isEmpty() || found.get().getStatus() == RoomStatus.CLOSED) return;
        var room = found.get();
        var members = new ArrayList<>(participants.findMembers(roomId));
        // 종료 요청 자체가 유실된 브라우저도 접속 만료 뒤 참가를 제거한다. 일시적인 대국 단절의 재접속 유예는 유지한다.
        if (room.getStatus() == RoomStatus.PLAYING) {
            var expired = members.stream().filter(p -> p.getLastSeenAt().isBefore(gomokuClock.instant().minusSeconds(180))).toList();
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
        // 대국 도중 모바일 백그라운드 스케줄러가 잠시 정지해도 150초 게임 복구 기한보다 먼저 퇴장시키지 않는다.
        Instant cutoff = Instant.now().minusSeconds(room.getStatus() == RoomStatus.PLAYING ? 180 : 90);
        var stale = members.stream().filter(p -> !p.getUser().getId().equals(requester)
                && (p.getLastSeenAt().isBefore(cutoff) || p.getUser().getStatus() != UserStatus.ACTIVE
                    || p.getUser().getDeletedAt() != null)).toList();
        if (stale.isEmpty()) return;
        participants.deleteAll(stale);
        members.removeAll(stale);
        membershipChanged(room, members);
        // 비정상 종료 참가자를 청소할 때도 Redis 채팅 권한과 SSE 상태를 커밋된 명단으로 동기화한다.
        chatAccess.changed(room, members);
        events.changed(room.getId());
        lobbyEvents.changed();
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
        return snapshot(room, members, viewer, true);
    }

    private WaitingRoomResponse snapshot(GomokuRoom room, List<GomokuRoomParticipant> members, Long viewer, boolean owner) {
        return snapshot(room, members, viewer, owner, owner);
    }

    private WaitingRoomResponse snapshot(GomokuRoom room, List<GomokuRoomParticipant> members, Long viewer,
                                         boolean owner, boolean refreshAccess) {
        // 진행 중에도 정상 참가 조회는 접속·채팅 리스를 유지하지만 숨긴 추첨 결과는 공개하지 않는다.
        // 화면 표시용 viewer와 접속 수명 갱신 권한을 분리해 다른 탭의 단순 조회를 차단한다.
        var game = games.synchronize(room, members, owner ? viewer : null);
        // 페이지 인증 전의 읽기 전용 조회는 Redis 참가 리스도 연장하지 않는다.
        if (owner && refreshAccess) chatAccess.changed(room, members);
        return WaitingRoomResponse.from(room, members, viewer, game, gomokuClock.instant(), playerStats.forMembers(members));
    }

    private BusinessException failure(ErrorCode code) { return new BusinessException(code); }
}

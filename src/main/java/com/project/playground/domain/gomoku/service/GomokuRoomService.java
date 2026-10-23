package com.project.playground.domain.gomoku.service;

import com.project.playground.domain.gomoku.dto.request.*;
import com.project.playground.domain.gomoku.dto.response.*;
import com.project.playground.domain.gomoku.entity.GomokuRoom;
import com.project.playground.domain.gomoku.entity.GomokuRoomParticipant;
import com.project.playground.domain.gomoku.enums.*;
import com.project.playground.domain.gomoku.repository.GomokuRoomQueryRepository;
import com.project.playground.domain.gomoku.repository.GomokuRoomRepository;
import com.project.playground.domain.gomoku.repository.GomokuRoomParticipantRepository;
import com.project.playground.domain.user.entity.User;
import com.project.playground.domain.user.enums.UserStatus;
import com.project.playground.domain.user.repository.UserRepository;
import com.project.playground.domain.user.repository.reader.UserReader;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import com.project.playground.domain.game.service.GameAvailabilityService;
import com.project.playground.domain.gomoku.module.GomokuModule;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.util.List;

/** 방장 신원·고정 규칙·초대 코드를 서버가 관리하며 읽기로 로그인 세션 수명을 연장하지 않는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GomokuRoomService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final GomokuRoomRepository rooms;
    private final GomokuRoomQueryRepository roomQueries;
    private final UserRepository users;
    private final UserReader userReader;
    private final PasswordEncoder passwordEncoder;
    private final RoomRateLimitService rateLimits;
    private final GomokuRoomParticipantRepository participants;
    private final RoomChatAccessPublisher chatAccess;
    private final GameAvailabilityService availability;

    public RoomPageResponse search(Long userId, SearchRoomsRequest request) {
        rateLimits.search(userId);
        userReader.findActiveById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        long total = roomQueries.count(request);
        int pages = (int) Math.ceil((double) total / GomokuRoomQueryRepository.PAGE_SIZE);
        int page = Math.min(request.getPage(), Math.max(0, pages - 1));
        return RoomPageResponse.builder().rooms(roomQueries.search(userId, request, page)).page(page)
                .pageSize(GomokuRoomQueryRepository.PAGE_SIZE).totalElements(total).totalPages(pages)
                .joinedRoom(participants.findCurrent(userId).map(p -> HostedRoomResponse.from(p.getRoom())).orElse(null))
                .hostedRoom(rooms.findByHostIdAndStatusNot(userId, RoomStatus.CLOSED).map(HostedRoomResponse::from).orElse(null)).build();
    }

    @Transactional
    public HostedRoomResponse create(Long userId, CreateRoomRequest request) {
        rateLimits.create(userId);
        // 생성과 최초 입장만 같은 계정 행으로 직렬화해 동일 requestId 재시도를 그대로 복원한다.
        User host = users.findForUpdateById(userId).filter(user -> user.getStatus() == UserStatus.ACTIVE && user.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        var previous = rooms.findByCreatorIdAndCreateRequestId(userId, request.getRequestId());
        if (previous.isPresent()) {
            // 시간 초과 후 같은 요청을 다시 보내도 새 방을 만들지 않는다. 닫힌 방은 되살리지 않는다.
            // 이미 나가서 방장을 넘긴 방의 코드를 과거 생성 요청으로 다시 받지 못하게 한다.
            if (previous.get().getStatus() == RoomStatus.CLOSED || !previous.get().getHost().getId().equals(userId)) {
                throw new BusinessException(ErrorCode.ROOM_REQUEST_EXPIRED);
            }
            return HostedRoomResponse.from(previous.get());
        }
        if (rooms.findByHostIdAndStatusNot(userId, RoomStatus.CLOSED).isPresent()) {
            throw new BusinessException(ErrorCode.ROOM_ALREADY_HOSTING);
        }
        // 방장과 참가자 모두 계정당 하나의 대기실만 사용할 수 있다.
        if (participants.findCurrent(userId).isPresent()) throw new BusinessException(ErrorCode.ROOM_ALREADY_JOINED);
        // 이미 생성한 요청의 재확인은 위에서 복원하고 새 방 생성만 게임/온라인 모드 정책을 검사한다.
        availability.requireNewPlay(GomokuModule.GAME, GomokuModule.ONLINE);
        String passwordHash = request.getVisibility() == RoomVisibility.PRIVATE ? passwordEncoder.encode(request.getPassword()) : null;
        var room = GomokuRoom.builder().host(host).createRequestId(request.getRequestId()).title(request.getTitle().strip())
                .visibility(request.getVisibility()).passwordHash(passwordHash).inviteCode(newInviteCode())
                .undoLimit(request.getUndoLimit()).forbiddenRule(request.getForbiddenRule()).build();
        try {
            rooms.saveAndFlush(room);
            var hostMember = participants.saveAndFlush(GomokuRoomParticipant.builder().room(room).user(host).seat((short) 1)
                    .joinedAtMicros(chatAccess.nowMicros()).build());
            chatAccess.changed(room, List.of(hostMember));
            return HostedRoomResponse.from(room);
        } catch (DataIntegrityViolationException exception) {
            // 코드의 사전 확인과 고유 제약을 함께 사용한다. 드문 동시 충돌도 내부 SQL을 응답하지 않는다.
            Throwable cause = exception.getMostSpecificCause();
            if (cause.getMessage() != null && cause.getMessage().contains("uk_gomoku_room_invite_code")) {
                throw new BusinessException(ErrorCode.ROOM_CODE_UNAVAILABLE);
            }
            // 참가와 생성이 동시에 도착한 경우에도 DB 고유 제약을 공개 상태 충돌로 변환한다.
            if (cause.getMessage() != null && cause.getMessage().contains("uk_gomoku_participant_user")) {
                throw new BusinessException(ErrorCode.ROOM_ALREADY_JOINED);
            }
            if (cause.getMessage() != null && cause.getMessage().contains("uk_gomoku_room_active_host")) {
                throw new BusinessException(ErrorCode.ROOM_ALREADY_HOSTING);
            }
            throw exception;
        }
    }

    @Transactional
    public void close(Long userId, Long roomId) {
        rateLimits.close(userId);
        // 생성과 닫기는 회원 확인 → 방 잠금 순서다. 방장 위임의 FK 검사와 교착하지 않도록 회원 행은 잠그지 않는다.
        readActiveUser(userId);
        var room = rooms.findForUpdateById(roomId).filter(value -> value.getHost().getId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));
        if (room.getStatus() == RoomStatus.CLOSED) return;
        if (room.getStatus() != RoomStatus.WAITING || room.getPlayerCount() != 1) throw new BusinessException(ErrorCode.ROOM_CANNOT_CLOSE);
        // 로비의 다른 탭에서 방을 닫더라도 현재 대기 중인 활성 탭의 소유권을 무시하지 않는다.
        // 방 화면에 접속하기 전에는 pageId가 없어 기존 방 닫기 흐름을 유지한다.
        var members = participants.findMembers(roomId);
        if (members.stream().anyMatch(member -> member.getPageId() != null && member.getExitDeadline() == null
                && !member.getLastSeenAt().isBefore(java.time.Instant.now().minusSeconds(90)))) {
            throw new BusinessException(ErrorCode.ROOM_PAGE_IN_USE);
        }
        // 기존 빈 방 닫기도 새 대기실 참가·채팅 권한을 함께 회수한다.
        participants.deleteAll(members);
        room.close();
        chatAccess.changed(room, List.of());
    }

    private User readActiveUser(Long id) {
        return users.findById(id).filter(user -> user.getStatus() == UserStatus.ACTIVE && user.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private String newInviteCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            var value = new StringBuilder(6);
            for (int index = 0; index < 6; index++) value.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            String code = value.toString();
            if (!rooms.existsByInviteCode(code)) return code;
        }
        throw new BusinessException(ErrorCode.ROOM_CODE_UNAVAILABLE);
    }
}

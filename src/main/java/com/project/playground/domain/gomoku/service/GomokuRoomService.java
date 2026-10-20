package com.project.playground.domain.gomoku.service;

import com.project.playground.domain.gomoku.dto.request.*;
import com.project.playground.domain.gomoku.dto.response.*;
import com.project.playground.domain.gomoku.entity.GomokuRoom;
import com.project.playground.domain.gomoku.enums.*;
import com.project.playground.domain.gomoku.repository.GomokuRoomQueryRepository;
import com.project.playground.domain.gomoku.repository.GomokuRoomRepository;
import com.project.playground.domain.user.entity.User;
import com.project.playground.domain.user.enums.UserStatus;
import com.project.playground.domain.user.repository.UserRepository;
import com.project.playground.domain.user.repository.reader.UserReader;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;

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

    public RoomPageResponse search(Long userId, SearchRoomsRequest request) {
        rateLimits.search(userId);
        userReader.findActiveById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        long total = roomQueries.count(request);
        int pages = (int) Math.ceil((double) total / GomokuRoomQueryRepository.PAGE_SIZE);
        int page = Math.min(request.getPage(), Math.max(0, pages - 1));
        return RoomPageResponse.builder().rooms(roomQueries.search(userId, request, page)).page(page)
                .pageSize(GomokuRoomQueryRepository.PAGE_SIZE).totalElements(total).totalPages(pages)
                .hostedRoom(rooms.findByHostIdAndStatusNot(userId, RoomStatus.CLOSED).map(HostedRoomResponse::from).orElse(null)).build();
    }

    @Transactional
    public HostedRoomResponse create(Long userId, CreateRoomRequest request) {
        rateLimits.create(userId);
        User host = lockActiveUser(userId);
        var previous = rooms.findByHostIdAndCreateRequestId(userId, request.getRequestId());
        if (previous.isPresent()) {
            // 시간 초과 후 같은 요청을 다시 보내도 새 방을 만들지 않는다. 닫힌 방은 되살리지 않는다.
            if (previous.get().getStatus() == RoomStatus.CLOSED) throw new BusinessException(ErrorCode.ROOM_REQUEST_EXPIRED);
            return HostedRoomResponse.from(previous.get());
        }
        if (rooms.findByHostIdAndStatusNot(userId, RoomStatus.CLOSED).isPresent()) {
            throw new BusinessException(ErrorCode.ROOM_ALREADY_HOSTING);
        }
        String passwordHash = request.getVisibility() == RoomVisibility.PRIVATE ? passwordEncoder.encode(request.getPassword()) : null;
        var room = GomokuRoom.builder().host(host).createRequestId(request.getRequestId()).title(request.getTitle().strip())
                .visibility(request.getVisibility()).passwordHash(passwordHash).inviteCode(newInviteCode())
                .undoLimit(request.getUndoLimit()).forbiddenRule(request.getForbiddenRule()).build();
        try {
            return HostedRoomResponse.from(rooms.saveAndFlush(room));
        } catch (DataIntegrityViolationException exception) {
            // 코드의 사전 확인과 고유 제약을 함께 사용한다. 드문 동시 충돌도 내부 SQL을 응답하지 않는다.
            Throwable cause = exception.getMostSpecificCause();
            if (cause.getMessage() != null && cause.getMessage().contains("uk_gomoku_room_invite_code")) {
                throw new BusinessException(ErrorCode.ROOM_CODE_UNAVAILABLE);
            }
            throw exception;
        }
    }

    @Transactional
    public void close(Long userId, Long roomId) {
        rateLimits.close(userId);
        // 생성과 닫기는 회원 → 방 순서로 잠가 서로 다른 순서의 잠금으로 교착하지 않게 한다.
        lockActiveUser(userId);
        var room = rooms.findForUpdateById(roomId).filter(value -> value.getHost().getId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));
        if (room.getStatus() == RoomStatus.CLOSED) return;
        if (room.getStatus() != RoomStatus.WAITING || room.getPlayerCount() != 1) throw new BusinessException(ErrorCode.ROOM_CANNOT_CLOSE);
        room.close();
    }

    private User lockActiveUser(Long id) {
        return users.findForUpdateById(id).filter(user -> user.getStatus() == UserStatus.ACTIVE && user.getDeletedAt() == null)
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

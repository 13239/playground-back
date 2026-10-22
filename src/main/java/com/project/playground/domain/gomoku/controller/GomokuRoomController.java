package com.project.playground.domain.gomoku.controller;

import com.project.playground.domain.gomoku.dto.request.*;
import com.project.playground.domain.gomoku.dto.response.*;
import com.project.playground.domain.gomoku.service.GomokuRoomService;
import com.project.playground.domain.gomoku.service.GomokuWaitingRoomService;
import com.project.playground.global.security.session.Session;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** BFF와 경로·메서드를 일치시켜 기존 Bearer·Redis·브라우저 증명 경계 안에서만 방을 관리한다. */
@RestController
@RequestMapping("/api/gomoku/rooms")
@RequiredArgsConstructor
public class GomokuRoomController {
    private final GomokuRoomService rooms;
    private final GomokuWaitingRoomService waiting;

    // 대기실 요청도 기존 BFF·JWT·브라우저 증명과 본인의 로그인 세션을 그대로 사용한다.
    @PostMapping("/join")
    public ResponseEntity<WaitingRoomResponse> join(@AuthenticationPrincipal(errorOnInvalidType = true) Session session,
                                                   @Valid @RequestBody JoinRoomRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(waiting.join(session.getId(), request));
    }

    @PostMapping("/state")
    public ResponseEntity<WaitingRoomResponse> state(@AuthenticationPrincipal(errorOnInvalidType = true) Session session,
                                                    @Valid @RequestBody RoomStateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(waiting.state(session.getId(), request.getRoomId()));
    }

    @PostMapping("/ready")
    public ResponseEntity<WaitingRoomResponse> ready(@AuthenticationPrincipal(errorOnInvalidType = true) Session session,
                                                    @Valid @RequestBody ReadyRoomRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(waiting.ready(session.getId(), request.getRoomId(), request.getReady()));
    }

    @PostMapping("/leave")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal(errorOnInvalidType = true) Session session,
                                      @Valid @RequestBody LeaveRoomRequest request) {
        waiting.leave(session.getId(), request.getRoomId(), request.getMembershipId());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    /** 페이지 종료도 일반 퇴장과 같은 JWT·BFF·브라우저 증명으로 검증하고 참가자·페이지를 대조한다. */
    @PostMapping("/lifecycle")
    public ResponseEntity<Void> lifecycle(@AuthenticationPrincipal(errorOnInvalidType = true) Session session,
                                          @Valid @RequestBody RoomPageRequest request) {
        waiting.page(session.getId(), request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/search")
    public ResponseEntity<RoomPageResponse> search(@AuthenticationPrincipal(errorOnInvalidType = true) Session session,
                                                 @Valid @RequestBody SearchRoomsRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rooms.search(session.getId(), request));
    }

    @PostMapping
    public ResponseEntity<HostedRoomResponse> create(@AuthenticationPrincipal(errorOnInvalidType = true) Session session,
                                                    @Valid @RequestBody CreateRoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(rooms.create(session.getId(), request));
    }

    @PostMapping("/close")
    public ResponseEntity<Void> close(@AuthenticationPrincipal(errorOnInvalidType = true) Session session,
                                      @Valid @RequestBody CloseRoomRequest request) {
        rooms.close(session.getId(), request.getRoomId());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}

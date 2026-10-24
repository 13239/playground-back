package com.project.playground.domain.gomoku.controller;

import com.project.playground.domain.gomoku.dto.request.*;
import com.project.playground.domain.gomoku.dto.response.*;
import com.project.playground.domain.gomoku.service.GomokuRoomService;
import com.project.playground.domain.gomoku.service.GomokuLobbyEvents;
import com.project.playground.domain.gomoku.service.RoomRateLimitService;
import com.project.playground.domain.gomoku.service.GomokuWaitingRoomService;
import com.project.playground.global.security.session.Session;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** BFF와 경로·메서드를 일치시켜 기존 Bearer·Redis·브라우저 증명 경계 안에서만 방을 관리한다. */
@RestController
@RequestMapping("/api/gomoku/rooms")
@RequiredArgsConstructor
public class GomokuRoomController {
    private final GomokuRoomService rooms;
    private final GomokuWaitingRoomService waiting;
    private final GomokuLobbyEvents lobbyEvents;
    private final RoomRateLimitService limits;

    // 대기실 요청도 기존 BFF·JWT·브라우저 증명과 본인의 로그인 세션을 그대로 사용한다.
    // 참가 트랜잭션 이후 상세 스냅샷의 선택적 필드 검증에 실패해도 입장 성공을 실패로 표시하지 않는다.
    // 입장 자체의 권한·정원·강퇴 검증은 기존 waiting.join()에서 그대로 처리한다.
    @PostMapping("/join")
    public ResponseEntity<JoinRoomReceiptResponse> join(@AuthenticationPrincipal(errorOnInvalidType = true) Session session,
                                                        @Valid @RequestBody JoinRoomRequest request) {
        WaitingRoomResponse joined = waiting.join(session.getId(), request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(JoinRoomReceiptResponse.builder().roomId(joined.getId()).build());
    }

    @PostMapping("/state")
    public ResponseEntity<WaitingRoomResponse> state(@AuthenticationPrincipal(errorOnInvalidType = true) Session session,
                                                    @Valid @RequestBody RoomStateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(waiting.state(session.getId(), request.getRoomId(), request.getPageId()));
    }

    @PostMapping("/ready")
    public ResponseEntity<WaitingRoomResponse> ready(@AuthenticationPrincipal(errorOnInvalidType = true) Session session,
                                                    @Valid @RequestBody ReadyRoomRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(waiting.ready(session.getId(), request.getRoomId(), request.getReady(), request.getPageId()));
    }

    /** 강퇴는 현재 방장의 문서와 최신 참가 상태를 검증해 상대 한 명에게만 적용한다. */
    @PostMapping("/kick")
    public ResponseEntity<WaitingRoomResponse> kick(@AuthenticationPrincipal(errorOnInvalidType = true) Session session,
                                                     @Valid @RequestBody KickRoomRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(waiting.kick(session.getId(), request));
    }

    @PostMapping("/leave")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal(errorOnInvalidType = true) Session session,
                                      @Valid @RequestBody LeaveRoomRequest request) {
        waiting.leave(session.getId(), request.getRoomId(), request.getMembershipId(), request.getPageId());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    /** 페이지 종료도 일반 퇴장과 같은 JWT·BFF·브라우저 증명으로 검증하고 참가자·페이지를 대조한다. */
    @PostMapping("/lifecycle")
    public ResponseEntity<Void> lifecycle(@AuthenticationPrincipal(errorOnInvalidType = true) Session session,
                                          @Valid @RequestBody RoomPageRequest request) {
        // 페이지 점유의 로그인 식별자는 요청 본문에서 받지 않고 검증된 인증 주체에서 전달한다.
        waiting.page(session.getId(), session.getSessionId(), request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    /** 로비 화면을 열고 있는 로그인 사용자에게 변경 신호만 전달한다. 조회 조건은 SSE에 실지 않는다. */
    @PostMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> events(@AuthenticationPrincipal(errorOnInvalidType = true) Session session) {
        limits.events(session.getId());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Accel-Buffering", "no")
                .body(lobbyEvents.subscribe(session.getId(), session.getSessionId()));
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

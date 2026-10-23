package com.project.playground.domain.gomoku.controller;

import com.project.playground.domain.gomoku.dto.request.GameActionRequest;
import com.project.playground.domain.gomoku.dto.request.RoomStateRequest;
import com.project.playground.domain.gomoku.dto.response.WaitingRoomResponse;
import com.project.playground.domain.gomoku.service.*;
import com.project.playground.global.security.session.Session;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 게임 명령과 POST 이벤트 연결도 기존 BFF·JWT·Origin·브라우저 증명 경계 안에서만 허용한다. */
@RestController @RequiredArgsConstructor @RequestMapping("/api/gomoku/rooms/game")
public class GomokuGameController {
    private final GomokuGameService games;
    private final GomokuWaitingRoomService waiting;
    private final GomokuGameEvents events;
    private final RoomRateLimitService limits;

    @PostMapping
    public ResponseEntity<WaitingRoomResponse> action(@AuthenticationPrincipal(errorOnInvalidType = true) Session session,
                                                     @Valid @RequestBody GameActionRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(games.action(session.getId(), request));
    }
    @PostMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> events(@AuthenticationPrincipal(errorOnInvalidType = true) Session session,
                                             @Valid @RequestBody RoomStateRequest request) {
        limits.events(session.getId());
        // 소유하지 않은 탭은 SSE 구독을 만들거나 게임 접속 시간을 갱신할 수 없다.
        if (request.getPageId() == null) throw new com.project.playground.global.exception.BusinessException(
                com.project.playground.global.exception.ErrorCode.ROOM_PAGE_IN_USE);
        waiting.state(session.getId(), request.getRoomId(), request.getPageId());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Accel-Buffering", "no")
                // 연결 이후에도 같은 로그인 세션의 운영 제한·로그아웃·만료를 재확인한다.
                .body(events.subscribe(session.getId(), session.getSessionId(), request.getRoomId()));
    }
}

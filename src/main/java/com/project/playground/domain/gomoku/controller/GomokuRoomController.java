package com.project.playground.domain.gomoku.controller;

import com.project.playground.domain.gomoku.dto.request.*;
import com.project.playground.domain.gomoku.dto.response.*;
import com.project.playground.domain.gomoku.service.GomokuRoomService;
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

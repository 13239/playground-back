package com.project.playground.domain.shooting.controller;

import com.project.playground.domain.shooting.dto.*;
import com.project.playground.domain.shooting.service.ShootingService;
import com.project.playground.domain.shooting.service.ShootingLobbyService;
import com.project.playground.global.security.session.Session;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 기존 BFF·세션·브라우저 증명을 통과한 사용자에게만 참가권과 공개 순위를 제공한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shooting")
public class ShootingController {
    private final ShootingService shooting;
    private final ShootingLobbyService lobby;

    /** 목록은 실제 대기실 임대와 친구 관계를 확인한 공개 정보만 제공한다. */
    @PostMapping("/rooms")
    public ResponseEntity<ShootingLobbyService.Page> rooms(
            @AuthenticationPrincipal(errorOnInvalidType = true) Session session,
            @Valid @RequestBody ShootingLobbyService.Query query) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(lobby.list(session.getId(), query));
    }

    @PostMapping("/ticket")
    public ResponseEntity<ShootingResponse.Ticket> ticket(
            @AuthenticationPrincipal(errorOnInvalidType = true) Session session,
            @Valid @RequestBody ShootingRequest.Ticket request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(shooting.ticket(session, request));
    }

    @PostMapping("/overview")
    public ResponseEntity<ShootingResponse.Overview> overview(
            @AuthenticationPrincipal(errorOnInvalidType = true) Session session,
            @Valid @RequestBody ShootingRequest.Overview request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(shooting.overview(session.getId(), request));
    }
}

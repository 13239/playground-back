package com.project.playground.domain.gomoku.controller;

import com.project.playground.domain.gomoku.dto.request.GomokuInvitationRequest;
import com.project.playground.domain.gomoku.dto.response.GomokuInvitationResponse;
import com.project.playground.domain.gomoku.service.GomokuInvitationService;
import com.project.playground.global.security.session.Session;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 기존 BFF·브라우저 증명·세션 경계에서 본인 초대만 처리한다. 임의 게임 경로는 허용하지 않는다. */
@RestController @RequiredArgsConstructor
@RequestMapping("/api/gomoku/invitations")
public class GomokuInvitationController {
    private final GomokuInvitationService invitations;

    @PostMapping("/send")
    public ResponseEntity<GomokuInvitationResponse> send(@AuthenticationPrincipal(errorOnInvalidType = true) Session session,
                                                        @Valid @RequestBody GomokuInvitationRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(invitations.send(session.getId(), session.getSessionId(), request));
    }

    @PostMapping("/{action:accept|reject|cancel}")
    public ResponseEntity<GomokuInvitationResponse.Decision> respond(@AuthenticationPrincipal(errorOnInvalidType = true) Session session,
            @Valid @RequestBody GomokuInvitationRequest request, @PathVariable String action) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(invitations.respond(session.getId(), request.getInvitationId(), action));
    }
}

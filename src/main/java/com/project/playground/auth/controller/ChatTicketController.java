package com.project.playground.auth.controller;

import com.project.playground.auth.dto.response.ChatTicketResponse;
import com.project.playground.auth.service.ChatTicketService;
import com.project.playground.global.security.session.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 기존 SecurityConfig의 BFF·JWT·브라우저 증명 검증을 모두 통과한 요청만 받는다. */
@RestController
@RequiredArgsConstructor
public class ChatTicketController {

    private final ChatTicketService tickets;

    @PostMapping("/api/chat/tickets")
    public ResponseEntity<ChatTicketResponse> issue(
            @AuthenticationPrincipal(errorOnInvalidType = true) Session session) {
        // 접속 증명 발급은 활동으로 취급하지 않아 로그인 세션 수명을 자동 연장하지 않는다.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tickets.issue(session));
    }
}

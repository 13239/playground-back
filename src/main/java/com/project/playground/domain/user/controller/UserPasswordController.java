package com.project.playground.domain.user.controller;

import com.project.playground.domain.user.dto.request.ChangePasswordRequest;
import com.project.playground.domain.user.service.UserPasswordService;
import com.project.playground.global.security.session.Session;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 비밀번호 변경은 브라우저 증명과 현재 세션으로 검증하며 원문이나 해시를 응답하지 않는다. */
@RestController
@RequiredArgsConstructor
public class UserPasswordController {

    private final UserPasswordService passwords;

    @PostMapping("/api/auth/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal(errorOnInvalidType = true) Session session,
            @Valid @RequestBody ChangePasswordRequest request) {
        passwords.changePassword(session, request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}

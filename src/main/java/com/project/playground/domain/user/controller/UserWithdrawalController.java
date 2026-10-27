package com.project.playground.domain.user.controller;

import com.project.playground.domain.user.dto.request.WithdrawAccountRequest;
import com.project.playground.domain.user.service.UserWithdrawalService;
import com.project.playground.global.security.session.Session;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 현재 인증된 본인만 탈퇴시키고 기존 JWT·Redis 세션·브라우저 증명 검증을 그대로 적용한다. */
@RestController
@RequiredArgsConstructor
public class UserWithdrawalController {
    private final UserWithdrawalService withdrawal;

    @PostMapping("/api/auth/withdrawal")
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal(errorOnInvalidType = true) Session session,
            @Valid @RequestBody WithdrawAccountRequest request) {
        withdrawal.withdraw(session, request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}

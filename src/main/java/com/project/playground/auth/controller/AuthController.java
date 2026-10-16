package com.project.playground.auth.controller;

import com.project.playground.auth.dto.request.LoginRequest;
import com.project.playground.auth.dto.request.RefreshRequest;
import com.project.playground.auth.dto.response.CurrentUserResponse;
import com.project.playground.auth.dto.response.LoginResponse;
import com.project.playground.auth.dto.response.SessionActivityResponse;
import com.project.playground.auth.service.AuthService;
import com.project.playground.global.security.session.Session;
import com.project.playground.global.support.CommonUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpServletRequest) {
        String clientIp = CommonUtils.getClientIp(httpServletRequest);

        LoginResponse response = authService.login(request, clientIp);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore()) // 토큰이 포함된 응답을 캐시에 저장하지 않도록 설정
                .body(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        LoginResponse response = authService.refresh(request);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore()) // 토큰이 포함된 응답을 캐시에 저장하지 않도록 설정
                .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal(errorOnInvalidType = true) Session session) {
        authService.logout(session);

        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore()) // 요청, 응답을 HTTP 캐시에 저장하지 않음을 명시
                .build();
    }

    // 실제 사용자 활동 시 세션 만료 시간 연장
    @PostMapping("/activity")
    public ResponseEntity<SessionActivityResponse> activity(@AuthenticationPrincipal Session session) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(authService.activity(session));
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me() {
        return ResponseEntity.ok(null);
    }
}

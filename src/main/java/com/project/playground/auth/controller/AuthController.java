package com.project.playground.auth.controller;

import com.project.playground.auth.dto.request.LoginRequest;
import com.project.playground.auth.dto.request.RefreshRequest;
import com.project.playground.auth.dto.response.CurrentUserResponse;
import com.project.playground.auth.dto.response.LoginResponse;
import com.project.playground.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);

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

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me(@AuthenticationPrincipal Jwt jwt) {
        CurrentUserResponse response = CurrentUserResponse.builder()
                .id(Long.valueOf(jwt.getSubject()))
                .build();

        return ResponseEntity.ok(response);
    }
}

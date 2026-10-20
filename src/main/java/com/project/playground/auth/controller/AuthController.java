package com.project.playground.auth.controller;

import com.project.playground.auth.clientip.LoginClientIpResolver;
import com.project.playground.auth.proof.BrowserProof;
import com.project.playground.auth.proof.BrowserProofService;
import com.project.playground.auth.dto.request.ProofChallengeRequest;
import com.project.playground.auth.dto.response.ProofChallengeResponse;
import com.project.playground.auth.dto.request.LoginRequest;
import com.project.playground.auth.dto.request.RefreshRequest;
import com.project.playground.auth.dto.response.CurrentSessionResponse;
import com.project.playground.auth.dto.response.LoginResponse;
import com.project.playground.auth.dto.response.SessionActivityResponse;
import com.project.playground.auth.service.AuthService;
import com.project.playground.global.security.session.Session;
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

    private final LoginClientIpResolver loginClientIpResolver;

    private final BrowserProofService browserProofService;

    // 인증 데이터 없이 nonce만 발급하며 세션·토큰·사용자 활동을 변경하지 않는다.
    @PostMapping("/proof-challenge")
    public ResponseEntity<ProofChallengeResponse> challenge(@Valid @RequestBody ProofChallengeRequest request, HttpServletRequest servletRequest) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(browserProofService.challenge(request, loginClientIpResolver.resolve(servletRequest)));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpServletRequest,
                                              @RequestAttribute(BrowserProof.ATTRIBUTE) BrowserProof proof) {
        // 실제 연결 상대를 검증한 전용 판별기로 로그인 시도 제한에 사용할 IP를 결정한다.
        String clientIp = loginClientIpResolver.resolve(httpServletRequest);

        LoginResponse response = authService.login(request, clientIp, proof);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore()) // 토큰이 포함된 응답을 캐시에 저장하지 않도록 설정
                .body(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request,
                                                @RequestAttribute(BrowserProof.ATTRIBUTE) BrowserProof proof) {
        LoginResponse response = authService.refresh(request, proof);

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
        SessionActivityResponse activity = authService.activity(session);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(activity);
    }

    // 현재 요청에서 검증한 Redis 세션으로 사용자 식별 정보와 세션 유지 정책을 반환한다.
    @GetMapping("/session")
    public ResponseEntity<CurrentSessionResponse> session(@AuthenticationPrincipal(errorOnInvalidType = true) Session session) {
        CurrentSessionResponse currentSessionResponse = CurrentSessionResponse.from(session);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore()) // 사용자별 인증 정보를 HTTP 캐시에 저장하지 않는다.
                .body(currentSessionResponse);
    }
}

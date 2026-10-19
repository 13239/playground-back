package com.project.playground.auth.service;

import com.project.playground.auth.dto.request.LoginRequest;
import com.project.playground.auth.dto.request.RefreshRequest;
import com.project.playground.auth.dto.response.LoginResponse;
import com.project.playground.auth.dto.response.SessionActivityResponse;
import com.project.playground.auth.ratelimit.LoginRateLimitService;
import com.project.playground.auth.repository.RedisSessionRepository;
import com.project.playground.auth.session.SessionExpirationPolicy;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import com.project.playground.global.security.CustomUserDetails;
import com.project.playground.global.security.jwt.JwtRefreshTokenService;
import com.project.playground.global.security.jwt.JwtTokenService;
import com.project.playground.global.security.session.RedisSession;
import com.project.playground.global.security.session.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;

    private final JwtTokenService jwtTokenService;

    private final JwtRefreshTokenService jwtRefreshTokenService;

    private final RedisSessionRepository redisSessionRepository;

    private final SessionExpirationPolicy sessionExpirationPolicy;

    private final LoginRateLimitService loginRateLimitService;

    private final TokenRefreshService tokenRefreshService;

    public LoginResponse login(LoginRequest request, String clientIp) {
        // 인증 시도 횟수를 먼저 증가시켜 동시 요청도 제한에 포함한다.
        String accountSubject = loginRateLimitService.beforeLogin(request.getUsername(), clientIp);

        Authentication authentication;

        try {
            authentication = authenticate(request);
        } catch (BadCredentialsException | AccountStatusException e) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();

        // 인증 성공 시 계정 카운터만 초기화하고, 실패하면 새 세션을 만들기 전에 중단한다.
        loginRateLimitService.onAuthenticationSuccess(accountSubject);

        Instant now = Instant.now();
        UUID sessionId = UUID.randomUUID();

        boolean keepLoggedIn = request.getKeepLoggedIn();

        Instant expiresAt = sessionExpirationPolicy.calculateInitialExpiresAt(keepLoggedIn, now);

        // 세션 ID를 포함하는 Refresh Token 생성
        String refreshToken = jwtRefreshTokenService.create(sessionId);

        // Redis 세션에는 Refresh Token 해시만 저장
        RedisSession session = RedisSession.builder()
                .sessionId(sessionId)
                .id(user.getId())
                .username(user.getUsername())
                .refreshToken(jwtRefreshTokenService.hash(refreshToken))
                .keepLoggedIn(keepLoggedIn)
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();

        // 사용자 ID와 세션 ID가 포함된 Access Token 발급
        Jwt accessToken = jwtTokenService.createAccessToken(user.getId(), sessionId);

        /*
         * 동시 로그인 최대 3개 제한이 적용된다.
         *
         * 계정이 차단된 경우 Redis에서 세션을 생성하지 않고 false를 반환하므로 로그인 요청을 거부한다.
         *
         * 차단 여부 확인과 세션 생성은 Lua Script에서 원자적으로 처리한다.
         */
        if (!redisSessionRepository.create(session)) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        return createLoginResponse(accessToken, refreshToken, keepLoggedIn, expiresAt);
    }

    // Refresh Token 갱신
    public LoginResponse refresh(RefreshRequest request) {
        return tokenRefreshService.refresh(request);
    }

    // 로그아웃
    public void logout(Session session) {
        redisSessionRepository.deleteBySessionId(session.getSessionId(), session.getId());
    }

    // 의미 있는 사용자 활동이 발생했을 때 세션 만료 시간을 연장한다.
    public SessionActivityResponse activity(Session session) {
        Instant expiresAt = redisSessionRepository.extendOnActivity(session.getSessionId(), session.getId(), sessionExpirationPolicy.getRememberTtl(), sessionExpirationPolicy.getIdleTtl())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SESSION));

        return SessionActivityResponse.builder()
                .keepLoggedIn(session.isKeepLoggedIn())
                .expiresAt(expiresAt)
                .build();
    }

    // 로그인 응답 생성
    private LoginResponse createLoginResponse(Jwt accessToken, String refreshToken, boolean keepLoggedIn, Instant sessionExpiresAt) {
        long expiresIn = Duration.between(accessToken.getIssuedAt(), accessToken.getExpiresAt()).toSeconds();

        return LoginResponse.builder()
                .accessToken(accessToken.getTokenValue())
                .refreshToken(refreshToken)
                .tokenType(OAuth2AccessToken.TokenType.BEARER.getValue())
                .expiresIn(expiresIn)
                .accessTokenExpiresAt(accessToken.getExpiresAt().truncatedTo(ChronoUnit.SECONDS))
                .keepLoggedIn(keepLoggedIn)
                .sessionExpiresAt(sessionExpiresAt)
                .build();
    }

    // 아이디와 비밀번호 인증
    private Authentication authenticate(LoginRequest request) {
        return authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(request.getUsername(), request.getPassword()));
    }
}

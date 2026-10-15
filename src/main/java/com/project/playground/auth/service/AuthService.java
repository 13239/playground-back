package com.project.playground.auth.service;

import com.project.playground.auth.dto.request.LoginRequest;
import com.project.playground.auth.dto.request.LogoutRequest;
import com.project.playground.auth.dto.request.RefreshRequest;
import com.project.playground.auth.dto.response.LoginResponse;
import com.project.playground.auth.dto.response.SessionActivityResponse;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;

    private final JwtTokenService jwtTokenService;

    private final JwtRefreshTokenService jwtRefreshTokenService;

    private final RedisSessionRepository redisSessionRepository;

    private final SessionExpirationPolicy sessionExpirationPolicy;

    public LoginResponse login(LoginRequest request) {
        Authentication authentication;

        try {
            authentication = authenticate(request);
        } catch (BadCredentialsException | AccountStatusException e) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();

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
        String oldToken = request.getRefreshToken();

        // Refresh Token에서 기존 로그인 세션 ID를 가져온다.
        UUID sessionId = jwtRefreshTokenService.extractSessionId(oldToken);

        /*
         * 유효한 Redis 세션인지 확인한다.
         * 계정 차단 여부도 find.lua에서 함께 검증한다.
         */
        RedisSession session = redisSessionRepository.findBySessionId(sessionId).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        /*
         * 비활성화되거나 삭제된 회원은 find.lua에서 먼저 거부한다.
         * 실제 Refresh Token 회전도 rotate.lua에서 차단 상태와 토큰 해시를 원자적으로 재검증하므로 MySQL 조회가 필요 없다.
         */

        // 새로운 Refresh Token을 생성하되 sessionId는 유지한다.
        String newToken = jwtRefreshTokenService.create(sessionId);

        Jwt accessToken = jwtTokenService.createAccessToken(session.getId(), sessionId);

        /*
         * 기존 토큰 해시가 일치하는 경우에만 Redis의 Refresh Token을 새 해시로 교체한다.
         *
         * 동시에 두 갱신 요청이 오더라도 하나만 성공한다.
         */
        boolean rotated = redisSessionRepository.rotateRefreshToken(sessionId, session.getId(), jwtRefreshTokenService.hash(oldToken), jwtRefreshTokenService.hash(newToken));

        if (!rotated) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // Refresh 자체는 세션 만료 시간을 연장하지 않는다.
        return createLoginResponse(accessToken, newToken, session.isKeepLoggedIn(), session.getExpiresAt());
    }

    // 로그아웃
    public void logout(LogoutRequest request) {
        String refreshToken = request.getRefreshToken();

        UUID sessionId = jwtRefreshTokenService.extractSessionId(refreshToken);

        // 이미 종료된 세션이나 차단된 계정의 로그아웃 요청은 그대로 종료한다.
        redisSessionRepository.findBySessionId(sessionId).ifPresent(session -> redisSessionRepository.deleteByRefreshToken(sessionId, session.getId(), jwtRefreshTokenService.hash(refreshToken)));
    }

    // 의미 있는 사용자 활동이 발생했을 때 세션 만료 시간을 연장한다.
    public SessionActivityResponse activity(Session session) {
        Instant expiresAt = redisSessionRepository.extendOnActivity(session.getSessionId(), session.getId(), sessionExpirationPolicy.getRememberTtl(), sessionExpirationPolicy.getIdleTtl()).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SESSION));

        return SessionActivityResponse.builder().keepLoggedIn(session.isKeepLoggedIn()).expiresAt(expiresAt).build();
    }

    // 로그인 응답 생성
    private LoginResponse createLoginResponse(Jwt accessToken, String refreshToken, boolean keepLoggedIn, Instant sessionExpiresAt) {
        long expiresIn = Duration.between(accessToken.getIssuedAt(), accessToken.getExpiresAt()).toSeconds();

        return LoginResponse.builder()
                .accessToken(accessToken.getTokenValue())
                .refreshToken(refreshToken)
                .tokenType(OAuth2AccessToken.TokenType.BEARER.getValue())
                .expiresIn(expiresIn)
                .keepLoggedIn(keepLoggedIn)
                .sessionExpiresAt(sessionExpiresAt)
                .build();
    }

    // 아이디와 비밀번호 인증
    private Authentication authenticate(LoginRequest request) {
        return authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(request.getUsername(), request.getPassword()));
    }
}

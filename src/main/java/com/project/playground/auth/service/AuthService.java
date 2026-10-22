package com.project.playground.auth.service;

import com.project.playground.auth.crypto.AuthResultCipher;
import com.project.playground.auth.proof.BrowserProof;
import com.project.playground.auth.dto.internal.LoginSessionReplacement;
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

    // 동시 재로그인에서도 같은 토큰을 반환하도록 로그인 결과를 짧게 암호화해 공유한다.
    private final AuthResultCipher authResultCipher;

    public LoginResponse login(LoginRequest request, String clientIp, BrowserProof proof) {
        // 인증 시도 횟수를 먼저 증가시켜 동시 요청도 제한에 포함한다.
        var loginAttempt = loginRateLimitService.beforeLogin(request.getUsername(), clientIp);

        Authentication authentication;

        try {
            authentication = authenticate(request);
        } catch (BadCredentialsException | AccountStatusException e) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();

        // 인증 성공 시 계정 카운터를 초기화하고 해당 IP 예약을 반환한다. 실패하면 새 세션을 만들기 전에 중단한다.
        loginRateLimitService.onAuthenticationSuccess(loginAttempt);

        Instant now = Instant.now();
        UUID sessionId = UUID.randomUUID();

        boolean keepLoggedIn = request.getKeepLoggedIn();

        Instant expiresAt = sessionExpirationPolicy.calculateInitialExpiresAt(keepLoggedIn, now);

        // 세션 ID와 최초 세대(0)를 포함하는 서명된 Refresh Token 생성
        String refreshToken = jwtRefreshTokenService.create(sessionId, 0);

        // Redis 세션에는 Refresh Token 해시만 저장
        RedisSession session = RedisSession.builder()
                .sessionId(sessionId)
                .id(user.getId())
                .username(user.getUsername())
                .refreshToken(jwtRefreshTokenService.hash(refreshToken))
                .browserKeyThumbprint(proof.getKeyThumbprint())
                .keepLoggedIn(keepLoggedIn)
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();

        // 사용자 ID와 세션 ID가 포함된 Access Token 발급
        Jwt accessToken = jwtTokenService.createAccessToken(user.getId(), sessionId);

        // 비밀번호 인증에 성공한 뒤에만 기존 세션의 서명된 교체 증명을 검증한다.
        LoginSessionReplacement replacement = resolveReplacement(request);
        String resultContext = "login:" + user.getId() + ":" + keepLoggedIn + ":" + proof.getKeyThumbprint() + ":"
                + (replacement == null ? sessionId : replacement.getSessionId() + ":" + replacement.getRefreshTokenHash());
        LoginResponse candidate = createLoginResponse(accessToken, refreshToken, keepLoggedIn, expiresAt, proof.getKeyThumbprint());

        /*
         * 계정당 로그인은 하나만 허용하며 다른 세션의 종료는 사용자가 교체에 동의한 경우에만 수행한다.
         *
         * 계정이 차단된 경우 Redis에서 세션을 생성하지 않고 빈 결과를 반환하므로 로그인 요청을 거부한다.
         *
         * 차단·중복 확인, 교체 동의 확인, 기존 세션 교체, 세션 생성과 중복 결과 공유는 Lua Script에서 원자적으로 처리한다.
         */
        String encryptedResult = redisSessionRepository.create(session, replacement,
                        authResultCipher.encrypt(candidate, resultContext), Boolean.TRUE.equals(request.getReplaceExistingSession()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        // 동시 요청은 먼저 성공한 한 번의 결과를 받으며 각자 새 세션을 만들지 않는다.
        return authResultCipher.decrypt(encryptedResult, resultContext, LoginResponse.class);
    }

    // 이전 JWT 키의 쿠키는 신규 비밀번호 인증을 막지 않는다. 검증할 수 없는 토큰으로 기존 세션을 삭제하지도 않는다.
    private LoginSessionReplacement resolveReplacement(LoginRequest request) {
        String previousRefreshToken = request.getPreviousRefreshToken();
        if (previousRefreshToken == null) return null;
        try {
            return LoginSessionReplacement.builder()
                    .sessionId(jwtRefreshTokenService.verify(previousRefreshToken).getSessionId())
                    .refreshTokenHash(jwtRefreshTokenService.hash(previousRefreshToken))
                    .refreshRequestHash(request.getPreviousRefreshRequestId() == null ? null
                            : jwtRefreshTokenService.hash(request.getPreviousRefreshRequestId()))
                    .build();
        } catch (BusinessException e) {
            // 무효한 교체 증명만 무시하며 저장소 장애 등 다른 실패는 정상 로그인으로 바꾸지 않는다.
            if (e.getErrorCode() != ErrorCode.INVALID_REFRESH_TOKEN) throw e;
            return null;
        }
    }

    // Refresh Token 갱신
    public LoginResponse refresh(RefreshRequest request, BrowserProof proof) {
        return tokenRefreshService.refresh(request, proof);
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
    private LoginResponse createLoginResponse(Jwt accessToken, String refreshToken, boolean keepLoggedIn, Instant sessionExpiresAt, String browserKeyThumbprint) {
        long expiresIn = Duration.between(accessToken.getIssuedAt(), accessToken.getExpiresAt()).toSeconds();

        return LoginResponse.builder()
                .accessToken(accessToken.getTokenValue())
                .refreshToken(refreshToken)
                .browserKeyThumbprint(browserKeyThumbprint)
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

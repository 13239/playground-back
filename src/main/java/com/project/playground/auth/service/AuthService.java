package com.project.playground.auth.service;

import com.project.playground.auth.crypto.AuthResultCipher;
import com.project.playground.auth.audit.LoginAuditContext;
import com.project.playground.auth.proof.BrowserProof;
import com.project.playground.auth.dto.internal.LoginRateLimitAttempt;
import com.project.playground.auth.dto.internal.LoginSessionReplacement;
import com.project.playground.auth.dto.request.LoginRequest;
import com.project.playground.auth.dto.request.RefreshRequest;
import com.project.playground.auth.dto.response.LoginResponse;
import com.project.playground.auth.dto.response.SessionActivityResponse;
import com.project.playground.auth.ratelimit.LoginRateLimitService;
import com.project.playground.auth.repository.RedisSessionRepository;
import com.project.playground.auth.session.SessionExpirationPolicy;
import com.project.playground.domain.accesspolicy.service.AccessPolicyAdmissionService;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import com.project.playground.global.security.CustomUserDetails;
import com.project.playground.global.security.LoginUserDetailsLookup;
import com.project.playground.global.security.jwt.JwtRefreshTokenService;
import com.project.playground.global.security.jwt.JwtTokenService;
import com.project.playground.global.security.session.RedisSession;
import com.project.playground.global.security.session.Session;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
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

    // 현재 DB 역할과 접속 정책에 결합한 세션만 발급한다.
    private final AccessPolicyAdmissionService accessAdmission;

    private final SessionExpirationPolicy sessionExpirationPolicy;

    private final LoginRateLimitService loginRateLimitService;

    private final TokenRefreshService tokenRefreshService;

    // 동시 재로그인에서도 같은 토큰을 반환하도록 로그인 결과를 짧게 암호화해 공유한다.
    private final AuthResultCipher authResultCipher;

    // 로그인 제한 단계에서 조회한 요청별 계정 정보를 활동 로그에 재사용하여 DB를 다시 조회하지 않는다.
    private final LoginUserDetailsLookup loginUserDetailsLookup;

    public LoginResponse login(LoginRequest request, String clientIp, BrowserProof proof) {
        LoginAuditContext.fact("username", request.getUsername());
        LoginAuditContext.fact("keepLoggedIn", request.getKeepLoggedIn());
        LoginAuditContext.fact("replacementRequested", Boolean.TRUE.equals(request.getReplaceExistingSession()));
        // 인증 시도 횟수를 먼저 증가시켜 동시 요청도 제한에 포함한다.
        var loginAttempt = loginRateLimitService.beforeLogin(request.getUsername(), clientIp);

        CustomUserDetails account = loginUserDetailsLookup.findByUsername(request.getUsername()).orElse(null);
        if (LoginAuditContext.current() != null && account != null) LoginAuditContext.current().user(account.getId());

        try {
            LoginResponse response = authenticateAndCreateSession(request, proof, loginAttempt);

            // 성공·실패 기록은 보안 필터/검증 실패까지 다루는 바깥 로그인 필터가 한 번만 저장한다.
            LoginAuditContext.step("BACKEND_COMPLETED");

            return response;
        } catch (RuntimeException e) {
            LoginAuditContext.exception(e);
            throw e;
        }
    }

    // 비밀번호 인증 후 Redis 로그인 세션을 생성하고 토큰을 발급한다.
    private LoginResponse authenticateAndCreateSession(LoginRequest request, BrowserProof proof, LoginRateLimitAttempt loginAttempt) {
        Authentication authentication;

        LoginAuditContext.step("PASSWORD_VERIFICATION");
        try {
            authentication = authenticate(request);
        } catch (BadCredentialsException e) {
            if (LoginAuditContext.current() != null) LoginAuditContext.current().mismatch(
                    loginUserDetailsLookup.findByUsername(request.getUsername()).isPresent());
            LoginAuditContext.exception(e);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        } catch (AccountStatusException e) {
            LoginAuditContext.reason("ACCOUNT_NOT_ACTIVE");
            LoginAuditContext.exception(e);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();
        LoginAuditContext.fact("credentialsVerified", true);

        // 인증 성공 시 계정 카운터를 초기화하고 해당 IP 예약을 반환한다. 실패하면 새 세션을 만들기 전에 중단한다.
        LoginAuditContext.step("RATE_LIMIT_RESET");
        loginRateLimitService.onAuthenticationSuccess(loginAttempt);

        Instant now = Instant.now();
        UUID sessionId = UUID.randomUUID();

        boolean keepLoggedIn = request.getKeepLoggedIn();

        LoginAuditContext.step("SESSION_EXPIRY");
        Instant expiresAt = sessionExpirationPolicy.calculateInitialExpiresAt(keepLoggedIn, now);

        // 세션 ID와 최초 세대(0)를 포함하는 서명된 Refresh Token 생성
        LoginAuditContext.step("REFRESH_TOKEN_ISSUE");
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
        LoginAuditContext.step("ACCESS_TOKEN_ISSUE");
        Jwt accessToken = jwtTokenService.createAccessToken(user.getId(), sessionId);

        // 비밀번호 인증에 성공한 뒤에만 기존 세션의 서명된 교체 증명을 검증한다.
        LoginAuditContext.step("PREVIOUS_SESSION_CHECK");
        LoginSessionReplacement replacement = resolveReplacement(request);
        String resultContext = "login:" + user.getId() + ":" + keepLoggedIn + ":" + proof.getKeyThumbprint() + ":"
                + (replacement == null ? sessionId : replacement.getSessionId() + ":" + replacement.getRefreshTokenHash());
        LoginAuditContext.step("LOGIN_RESPONSE_BUILDING");
        LoginResponse candidate = createLoginResponse(accessToken, refreshToken, keepLoggedIn, expiresAt, proof.getKeyThumbprint());

        /*
         * 계정당 로그인은 하나만 허용하며 다른 세션의 종료는 사용자가 교체에 동의한 경우에만 수행한다.
         *
         * 계정이 차단된 경우 Redis에서 세션을 생성하지 않고 빈 결과를 반환하므로 로그인 요청을 거부한다.
         *
         * 차단·중복 확인, 교체 동의 확인, 기존 세션 교체, 세션 생성과 중복 결과 공유는 Lua Script에서 원자적으로 처리한다.
         */
        LoginAuditContext.step("RESULT_ENCRYPTION");
        String candidateResult = authResultCipher.encrypt(candidate, resultContext);
        String encryptedResult = accessAdmission.create(session, replacement,
                candidateResult, Boolean.TRUE.equals(request.getReplaceExistingSession()), user.getPassword());

        // 동시 요청은 먼저 성공한 한 번의 결과를 받으며 각자 새 세션을 만들지 않는다.
        LoginAuditContext.step("RESULT_DECRYPTION");
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

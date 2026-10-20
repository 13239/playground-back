package com.project.playground.auth.service;

import com.project.playground.auth.crypto.AuthResultCipher;
import com.project.playground.auth.proof.BrowserProof;
import com.project.playground.auth.proof.BrowserProofService;
import com.project.playground.auth.dto.internal.RefreshTokenPair;
import com.project.playground.auth.dto.request.RefreshRequest;
import com.project.playground.auth.dto.response.LoginResponse;
import com.project.playground.auth.repository.RedisSessionRepository;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import com.project.playground.global.security.jwt.JwtRefreshTokenService;
import com.project.playground.global.security.jwt.JwtTokenService;
import com.project.playground.global.security.jwt.RefreshTokenClaims;
import com.project.playground.global.security.session.RedisSession;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * 서버 수에 관계없이 Redis 원자 연산으로 토큰 회전과 동일 갱신의 결과 공유를 보장한다.
 */
@Service
@RequiredArgsConstructor
public class TokenRefreshService {

    private final RedisSessionRepository redisSessionRepository;

    private final JwtRefreshTokenService jwtRefreshTokenService;

    private final JwtTokenService jwtTokenService;

    private final AuthResultCipher authResultCipher;

    private final BrowserProofService browserProofService;

    public LoginResponse refresh(RefreshRequest request, BrowserProof proof) {
        try {
            return refreshTokens(request, proof);
        } catch (DataAccessException e) {
            // Redis 장애는 인증 무효와 구분하며 DB를 대체 인증 수단으로 조회하지 않는다.
            throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
        }
    }

    private LoginResponse refreshTokens(RefreshRequest request, BrowserProof proof) {
        String oldToken = request.getRefreshToken();

        // Refresh Token의 서명·용도를 먼저 검증한 뒤 기존 로그인 세션 ID와 세대를 가져온다.
        // 위조한 세션 ID·세대가 Redis 조회나 재사용에 의한 세션 폐기에 도달하지 않게 한다.
        RefreshTokenClaims claims = jwtRefreshTokenService.verify(oldToken);
        UUID sessionId = claims.getSessionId();
        long generation = claims.getGeneration();
        if (generation == JwtRefreshTokenService.MAX_GENERATION) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        /*
         * 유효한 Redis 세션인지 확인한다.
         * 계정 차단 여부도 find.lua에서 함께 검증한다.
         */
        RedisSession session = redisSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        // local/prod는 키 소유를 증명하지 못하면 회전·재사용 판별에 도달하지 않는다. dev는 명시적 미등록 세션만 예외로 허용한다.
        browserProofService.requireSession(proof, sessionId, session.getBrowserKeyThumbprint());

        /*
         * 비활성화되거나 삭제된 회원은 find.lua에서 먼저 거부한다.
         * 실제 Refresh Token 회전도 rotate.lua에서 차단 상태와 토큰 해시·세대를 원자적으로 재검증하므로 MySQL 조회가 필요 없다.
         */
        String oldHash = jwtRefreshTokenService.hash(oldToken);
        String requestHash = jwtRefreshTokenService.hash(request.getRequestId());
        String context = sessionId + ":" + oldHash + ":" + requestHash;

        // 새로운 Refresh Token은 sessionId를 유지하고 검증한 이전 세대보다 한 단계 증가시킨다.
        String newToken = jwtRefreshTokenService.create(sessionId, generation + 1);
        Jwt accessToken = jwtTokenService.createAccessToken(session.getId(), sessionId);
        RefreshTokenPair candidate = RefreshTokenPair.builder()
                .accessToken(accessToken.getTokenValue())
                .refreshToken(newToken)
                // JWT NumericDate와 같은 초 정밀도로 쿠키에 전달한다.
                .accessTokenExpiresAt(accessToken.getExpiresAt().truncatedTo(ChronoUnit.SECONDS)).build();

        /*
         * 기존 토큰 해시와 세대가 일치하는 경우에만 Redis의 Refresh Token을 새 해시·세대로 교체한다.
         * 동시에 여러 요청이 와도 회전은 한 번만 수행하며 동일한 BFF 갱신 식별자의 결과만 최대 60초간 공유한다.
         * 공유 결과를 읽을 때도 현재 세션·계정 차단·회전 이후 토큰 해시·세대를 Lua에서 다시 검증한다.
         * 이 예외에 해당하지 않는 검증된 이전 세대 토큰은 해당 세션과 목록을 즉시 폐기한다.
         */
        var result = redisSessionRepository.rotateRefreshToken(sessionId, session.getId(), generation, oldHash,
                        jwtRefreshTokenService.hash(newToken), requestHash, authResultCipher.encrypt(candidate, context))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        RefreshTokenPair tokens = authResultCipher.decrypt(result.getEncryptedTokens(), context, RefreshTokenPair.class);
        // 공통 암호화 계층에서 복원한 뒤 갱신 응답에 필요한 필드를 확인한다.
        if (tokens.getAccessToken() == null || tokens.getRefreshToken() == null || tokens.getAccessTokenExpiresAt() == null) {
            throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
        }
        long remainingMillis = Duration.between(Instant.now(), tokens.getAccessTokenExpiresAt()).toMillis();
        if (remainingMillis <= 0) throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);

        // Refresh 자체는 세션 만료 시간을 연장하지 않는다. 세션 정책은 Lua가 읽은 최신 값을 사용한다.
        return LoginResponse.builder()
                .accessToken(tokens.getAccessToken()).refreshToken(tokens.getRefreshToken())
                .browserKeyThumbprint(session.getBrowserKeyThumbprint())
                .tokenType(OAuth2AccessToken.TokenType.BEARER.getValue())
                .expiresIn(Math.max(1, remainingMillis / 1000))
                .accessTokenExpiresAt(tokens.getAccessTokenExpiresAt())
                .keepLoggedIn(result.isKeepLoggedIn()).sessionExpiresAt(result.getSessionExpiresAt()).build();
    }
}

package com.project.playground.auth.proof;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.SignedJWT;
import com.project.playground.auth.dto.request.ProofChallengeRequest;
import com.project.playground.auth.dto.response.ProofChallengeResponse;
import com.project.playground.auth.repository.RedisSessionRepository;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import com.project.playground.global.security.jwt.JwtRefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import javax.crypto.KDF;
import javax.crypto.SecretKey;
import javax.crypto.spec.HKDFParameterSpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** BFF 세션용 요청 서명이다. OAuth 토큰을 브라우저에 공개하지 않으므로 표준 OAuth DPoP와 구분한다. */
@Service
public class BrowserProofService {

    public static final String HEADER = "X-Browser-Proof";

    private static final String TYPE = "bff-session-proof+jwt";

    private static final RedisScript<Long> REPLAY_SCRIPT = RedisScript.of(new ClassPathResource("redis/session/proof-replay.lua"), Long.class);

    private static final RedisScript<Long> CHALLENGE_LIMIT_SCRIPT = RedisScript.of(new ClassPathResource("redis/session/proof-challenge-limit.lua"), Long.class);

    private final StringRedisTemplate redis;
    private final RedisSessionRepository sessions;
    private final JwtRefreshTokenService refreshTokens;
    private final JwtEncoder nonceEncoder;
    private final JwtDecoder nonceDecoder;
    private final String origin;
    private final int maxPending;
    private final int maxLoginPending;

    public BrowserProofService(StringRedisTemplate redis, RedisSessionRepository sessions, JwtRefreshTokenService refreshTokens,
                               @Qualifier("jwtSecretKey") SecretKey secret,
                               @Value("${auth.proof.origin:http://localhost:3000}") String origin,
                               @Value("${auth.proof.max-pending:120}") int maxPending,
                               @Value("${auth.proof.login-max-pending:600}") int maxLoginPending) {
        this.redis = redis;
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        URI uri = URI.create(origin);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || uri.getPath().equals("/")) || maxPending < 10 || maxPending > 10000
                || maxLoginPending < 10 || maxLoginPending > 10000) {
            throw new IllegalArgumentException("브라우저 증명 설정이 올바르지 않습니다.");
        }
        this.origin = origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
        this.maxPending = maxPending;
        this.maxLoginPending = maxLoginPending;
        try {
            SecretKey key = KDF.getInstance("HKDF-SHA256").deriveKey("HmacSHA256", HKDFParameterSpec.ofExtract().addIKM(secret)
                    .thenExpand("playground:browser-nonce:v1".getBytes(StandardCharsets.UTF_8), 32));
            nonceEncoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).validateType(false).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTypeValidator("bff-nonce+jwt"),
                    new JwtIssuerValidator("playground-proof"), new JwtAudienceValidator(this.origin), new JwtTimestampValidator(java.time.Duration.ZERO)));
            nonceDecoder = decoder;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("브라우저 nonce 키를 초기화하지 못했습니다.");
        }
    }

    // 로그인 nonce는 무상태로 발급한다. 세션 nonce는 기존 서명 토큰과 등록된 공개키 지문을 확인한다.
    public ProofChallengeResponse challenge(ProofChallengeRequest request, String clientIp) {
        // 무상태 nonce라도 공격자가 발급·세션 조회 비용을 무제한 발생시키지 못하게 검증한 IP별로 제한한다.
        // 공개 로그인은 IP별, 인증된 세션은 세션·IP별로 분리해 공용 네트워크의 다른 계정과 예산을 공유하지 않는다.
        String scope = "login";
        if (request.getRefreshToken() != null) {
            UUID sid = refreshTokens.verify(request.getRefreshToken()).getSessionId();
            var session = sessions.findBySessionId(sid).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SESSION));
            if (!same(request.getKeyThumbprint(), session.getBrowserKeyThumbprint())) {
                throw new BusinessException(ErrorCode.BROWSER_KEY_REQUIRED);
            }
            scope = sid.toString();
        }
        Long acquired = redis.execute(CHALLENGE_LIMIT_SCRIPT,
                List.of("auth:proof:challenge:" + scope + ":" + refreshTokens.hash(clientIp)),
                Integer.toString(scope.equals("login") ? maxLoginPending : maxPending));
        if (acquired == null) throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
        if (acquired != 1) throw new BusinessException(ErrorCode.BROWSER_PROOF_RATE_LIMIT_EXCEEDED);
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant expires = now.plusSeconds(60);
        var claims = JwtClaimsSet.builder().issuer("playground-proof").audience(List.of(origin)).issuedAt(now).expiresAt(expires)
                .id(UUID.randomUUID().toString()).claim("scope", scope).claim("jkt", request.getKeyThumbprint()).build();
        String nonce = nonceEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("bff-nonce+jwt").build(), claims)).getTokenValue();
        return ProofChallengeResponse.builder().nonce(nonce).serverTime(now).expiresAt(expires).build();
    }

    // 공개키·ES256만 허용한다. 요청 내용의 해시는 mTLS로 인증된 BFF가 실제 브라우저 본문에서 계산한다.
    public BrowserProof verify(HttpServletRequest request, String expectedScope, String expectedKey, String loginClientIp) {
        try {
            String value = request.getHeader(HEADER);
            if (value == null || value.length() > 4096 || java.util.Collections.list(request.getHeaders(HEADER)).size() != 1) throw invalid();
            SignedJWT proof = SignedJWT.parse(value);
            var header = proof.getHeader();
            if (!JWSAlgorithm.ES256.equals(header.getAlgorithm()) || !new JOSEObjectType(TYPE).equals(header.getType())
                    || header.getCriticalParams() != null || !(header.getJWK() instanceof ECKey key)
                    || key.isPrivate() || !Curve.P_256.equals(key.getCurve()) || !proof.verify(new ECDSAVerifier(key))) throw invalid();
            String thumbprint = key.computeThumbprint().toString();
            var claims = proof.getJWTClaimsSet();
            String nonceValue = claims.getStringClaim("nonce");
            if (nonceValue == null || nonceValue.length() > 2048) throw invalid();
            Jwt nonce;
            try { nonce = nonceDecoder.decode(nonceValue); }
            catch (JwtValidationException e) { throw new BusinessException(ErrorCode.BROWSER_PROOF_EXPIRED); }
            String scope = nonce.getClaimAsString("scope");
            String bodyHash = request.getHeader("X-Browser-Body-SHA256");
            Instant now = Instant.now();
            var issuedAt = claims.getIssueTime();
            String id = claims.getJWTID();
            // 관리자 목록의 검색·커서와 화면의 세션을 함께 결합한다. 기존 API의 서명 계약은 유지한다.
            boolean adminRequest = request.getRequestURI().startsWith("/api/admin/");
            String target = origin + request.getRequestURI()
                    + (adminRequest && request.getQueryString() != null ? "?" + request.getQueryString() : "");
            if (!same(thumbprint, nonce.getClaimAsString("jkt")) || nonce.getExpiresAt() == null || nonce.getIssuedAt() == null
                    || nonce.getExpiresAt().isAfter(now.plusSeconds(65)) || scope == null
                    || !(scope.equals("login") || UUID.fromString(scope).toString().equals(scope))
                    || expectedScope != null && !scope.equals(expectedScope)
                    || expectedKey != null && !same(thumbprint, expectedKey)
                    || !request.getMethod().equals(claims.getStringClaim("htm"))
                    || !target.equals(claims.getStringClaim("htu"))
                    || adminRequest && (expectedScope == null || !expectedScope.equals(claims.getStringClaim("sid")))
                    || bodyHash == null || !bodyHash.matches("[A-Za-z0-9_-]{43}") || !same(bodyHash, claims.getStringClaim("bht"))
                    || issuedAt == null || issuedAt.toInstant().isAfter(now.plusSeconds(5))
                    || issuedAt.toInstant().isBefore(nonce.getIssuedAt().minusSeconds(5))
                    || id == null || !id.matches("[A-Za-z0-9_-]{16,64}")) throw invalid();
            // Redis는 검증된 서명에 대해서만 사용한다. 유효 기간 안의 항목을 조기 제거하지 않아 재전송을 허용하지 않는다.
            String replayScope = scope.equals("login") ? "login:" + refreshTokens.hash(loginClientIp) : scope;
            Long result = redis.execute(REPLAY_SCRIPT, List.of("auth:proof:used:" + replayScope), id,
                    Long.toString(nonce.getExpiresAt().toEpochMilli()), Integer.toString(scope.equals("login") ? maxLoginPending : maxPending));
            if (result == null) throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
            if (result == 0) throw new BusinessException(ErrorCode.BROWSER_PROOF_REPLAYED);
            if (result == -2) throw new BusinessException(ErrorCode.BROWSER_PROOF_RATE_LIMIT_EXCEEDED);
            if (result != 1) throw new BusinessException(ErrorCode.BROWSER_PROOF_EXPIRED);
            return BrowserProof.builder().scope(scope).keyThumbprint(thumbprint).build();
        } catch (BusinessException | org.springframework.dao.DataAccessException e) {
            throw e;
        } catch (Exception e) {
            // JOSE 예외에는 증명·nonce가 포함될 수 있으므로 원문이나 원인을 로그에 남기지 않는다.
            throw invalid();
        }
    }

    public void requireSession(BrowserProof proof, UUID sid, String thumbprint) {
        // 서명 검증을 끈 dev 필터만 이 내부 결과를 만든다. 키가 등록된 세션으로 예외를 확장하지 않는다.
        if (BrowserProof.DEV_HTTP_BINDING.equals(thumbprint) && BrowserProof.DEV_HTTP_BINDING.equals(proof.getScope())
                && BrowserProof.DEV_HTTP_BINDING.equals(proof.getKeyThumbprint())) return;
        if (!sid.toString().equals(proof.getScope()) || !same(thumbprint, proof.getKeyThumbprint())) throw invalid();
    }

    private static boolean same(String first, String second) {
        return first != null && second != null && MessageDigest.isEqual(first.getBytes(StandardCharsets.US_ASCII), second.getBytes(StandardCharsets.US_ASCII));
    }

    private static BusinessException invalid() { return new BusinessException(ErrorCode.BROWSER_PROOF_INVALID); }
}

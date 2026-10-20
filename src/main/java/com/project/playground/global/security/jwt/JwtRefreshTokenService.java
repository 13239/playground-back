package com.project.playground.global.security.jwt;

import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import javax.crypto.KDF;
import javax.crypto.SecretKey;
import javax.crypto.spec.HKDFParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** 서명된 세션 ID·세대 번호로 재사용을 판별하며 Access Token과 서명 키·대상·타입을 분리한다. */
@Service
public class JwtRefreshTokenService {

    public static final int MAX_TOKEN_LENGTH = 1024;

    public static final String TOKEN_PATTERN = "[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]{43}";

    // Redis Lua의 정수 비교가 정확한 범위 안에서만 세대를 증가시킨다.
    public static final long MAX_GENERATION = 9_007_199_254_740_991L;

    private static final String TOKEN_TYPE = "rt+jwt";

    private static final int RANDOM_BYTE_LENGTH = 32;

    private static final Pattern TOKEN_FORMAT = Pattern.compile(TOKEN_PATTERN);

    private static final Pattern RANDOM_ID_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final SecureRandom secureRandom = new SecureRandom();

    private final JwtEncoder encoder;

    private final JwtDecoder decoder;

    private final JwtProperties properties;

    private final String audience;

    public JwtRefreshTokenService(@Qualifier("jwtSecretKey") SecretKey jwtSecretKey, JwtProperties properties) {
        this.properties = properties;
        audience = properties.audience() + ":refresh";

        try {
            // Java 25 HKDF로 갱신 서명 전용 키를 파생하여 Access Token·결과 암호화와 키 용도를 분리한다.
            SecretKey signingKey = KDF.getInstance("HKDF-SHA256").deriveKey("HmacSHA256",
                    HKDFParameterSpec.ofExtract().addIKM(jwtSecretKey)
                            .thenExpand("playground:refresh-token-signing:v1".getBytes(StandardCharsets.UTF_8), 32));
            encoder = NimbusJwtEncoder.withSecretKey(signingKey).algorithm(MacAlgorithm.HS256).build();
            NimbusJwtDecoder refreshDecoder = NimbusJwtDecoder.withSecretKey(signingKey)
                    .macAlgorithm(MacAlgorithm.HS256).validateType(false).build();
            // 기본 JWT 타입 대신 갱신 전용 타입을 요구하며 서명·발급자·대상·사용 시작 시각을 검증한다.
            // 시간 검증은 Access Token과 같은 기본 시계 오차 허용치를 사용해 서버 간 작은 시각 차이를 수용한다.
            refreshDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    new JwtTimestampValidator(), new JwtIssuerValidator(properties.issuer()),
                    new JwtAudienceValidator(audience), new JwtTypeValidator(TOKEN_TYPE)));
            decoder = refreshDecoder;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Refresh Token 서명 키를 초기화하지 못했습니다.");
        }
    }

    // 세션 ID·세대 번호와 무작위 값(32바이트)을 함께 서명한 Refresh Token 생성
    public String create(UUID sessionId, long generation) {
        if (generation < 0 || generation > MAX_GENERATION) {
            throw new IllegalArgumentException("Refresh Token 세대 번호가 올바르지 않습니다.");
        }
        byte[] random = new byte[RANDOM_BYTE_LENGTH];
        secureRandom.nextBytes(random);
        Instant now = Instant.now();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type(TOKEN_TYPE).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer()).audience(List.of(audience))
                .claim("sid", sessionId.toString()).claim("gen", generation)
                .id(Base64.getUrlEncoder().withoutPadding().encodeToString(random))
                .issuedAt(now).notBefore(now)
                // 수명은 Redis의 활동 기반 만료로만 판단한다. 고정 exp로 활동 중인 세션을 강제 종료하지 않는다.
                .build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    // Refresh Token의 서명을 먼저 검증한 뒤 포함된 세션 ID와 세대 번호를 추출한다.
    public RefreshTokenClaims verify(String refreshToken) {
        if (refreshToken == null || refreshToken.length() > MAX_TOKEN_LENGTH
                || !TOKEN_FORMAT.matcher(refreshToken).matches()) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        try {
            Jwt jwt = decoder.decode(refreshToken);
            String sid = jwt.getClaimAsString("sid");
            UUID sessionId = UUID.fromString(sid);
            long generation = switch (jwt.getClaim("gen")) {
                case Long value -> value;
                case Integer value -> value.longValue();
                case null, default -> throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
            };
            // 소수·문자열·범위 밖 숫자를 세대로 해석하지 않으며 필수 식별 정보도 확인한다.
            if (generation < 0 || generation > MAX_GENERATION
                    || !sessionId.toString().equals(sid) || jwt.getIssuedAt() == null || jwt.getNotBefore() == null
                    || jwt.getId() == null || !RANDOM_ID_FORMAT.matcher(jwt.getId()).matches()) {
                throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
            }
            return RefreshTokenClaims.builder().sessionId(sessionId).generation(generation).build();
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            // 검증 오류의 원문·원인에는 토큰이 포함될 수 있으므로 외부 응답과 로그로 전달하지 않는다.
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    // Redis에는 Refresh Token 원본 대신 SHA-256 해시를 저장
    public String hash(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hashed = digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}

package com.project.playground.global.security.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private static final int REFRESH_TOKEN_BYTE_LENGTH = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    private final JwtEncoder jwtEncoder;

    private final JwtProperties jwtProperties;

    public Jwt createAccessToken(Long id) {
        Instant now = Instant.now();

        // 서명 알고리즘과 토큰 형식을 지정
        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer()) // 발급자
                .audience(List.of(jwtProperties.audience())) // 대상 API
                .subject(id.toString()) // 사용자 PK
                .id(UUID.randomUUID().toString()) // 토큰 고유 ID
                .issuedAt(now) // 발급 시간
                .notBefore(now) // 사용 시작 시간
                .expiresAt(now.plus(jwtProperties.accessTokenTtl())) // 만료 시간
                .build();

        // 헤더와 클레임을 비밀키로 서명해 JWT 발급
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims));
    }

    public String createRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }
}

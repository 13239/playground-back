package com.project.playground.global.security.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;

    private final JwtProperties jwtProperties;

    // 사용자와 로그인 세션이 연결된 JWT 발급
    public Jwt createAccessToken(Long id, UUID sessionId) {
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
                .claim("sid", sessionId.toString())
                .issuedAt(now) // 발급 시간
                .notBefore(now) // 사용 시작 시간
                .expiresAt(now.plus(jwtProperties.accessTokenTtl())) // 만료 시간
                .build();

        // 헤더와 클레임을 비밀키로 서명해 JWT 발급
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims));
    }
}

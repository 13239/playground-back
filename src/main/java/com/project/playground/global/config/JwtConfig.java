package com.project.playground.global.config;

import com.project.playground.global.security.jwt.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties(JwtProperties.class) // JwtProperties를 Bean으로 등록
public class JwtConfig {

    private static final String HMAC_SHA256 = "HmacSHA256";

    // JWT 발급과 검증에 사용할 공통 비밀키
    @Bean
    public SecretKey jwtSecretKey(JwtProperties properties) {
        byte[] keyBytes = Base64.getDecoder().decode(properties.secret());

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT 비밀키는 Base64 디코딩 후 최소 32바이트여야 합니다.");
        }

        return new SecretKeySpec(keyBytes, HMAC_SHA256);
    }

    // 비밀키와 HS256 알고리즘으로 JWT를 발급
    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return NimbusJwtEncoder
                .withSecretKey(jwtSecretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    // 발급할 때와 같은 비밀키로 JWT를 검증
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        // 아래 검증 조건을 모두 통과해야 유효한 토큰으로 인정
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(properties.issuer()), // 만료 시간 등 기본 검증 + 발급자(iss) 검증
                        new JwtAudienceValidator(properties.audience()) // 이 API를 대상으로 발급된 토큰인지 검증(aud)
                )
        );

        return decoder;
    }
}

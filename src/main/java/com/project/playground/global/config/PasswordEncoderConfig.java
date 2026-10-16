package com.project.playground.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 비밀번호 해시에 적용할 인코딩 정책 ID
        String encodeId = "argon2_v1";

        // 메모리 비용과 반복 횟수를 명시적으로 설정한다.
        PasswordEncoder argon2_v1 = new Argon2PasswordEncoder(
                16,        // Salt 길이: 16바이트, 인코딩마다 무작위 생성
                32,        // Hash 길이: 32바이트
                1,         // 병렬도
                19 * 1024, // 메모리 비용: KiB 단위, 19MiB
                2          // 반복 횟수
        );

        return new DelegatingPasswordEncoder(
                encodeId,
                Map.of(
                        "argon2_v1", argon2_v1 // 추후 버전 확장 시 이 부분은 유지하고 v2, v3 ... 으로 Map에 추가하면 됨
                )
        );
    }
}

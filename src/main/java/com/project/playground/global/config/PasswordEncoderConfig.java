package com.project.playground.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        String encodeId = "argon2_v5_8";

        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("argon2_v5_8", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());

        return new DelegatingPasswordEncoder(encodeId, encoders);
    }
}

package com.project.playground.global.security.jwt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(

        @NotBlank
        String issuer,

        @NotBlank
        String audience,

        @NotBlank
        String secret,

        @NotNull
        @DurationMin(seconds = 1)
        Duration accessTokenTtl
) {
}

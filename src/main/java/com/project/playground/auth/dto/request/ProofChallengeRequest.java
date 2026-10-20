package com.project.playground.auth.dto.request;

import com.project.playground.global.security.jwt.JwtRefreshTokenService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 공개키 지문과 선택적인 쿠키 토큰으로 짧은 nonce를 요청한다. 토큰 원문은 BFF만 전달한다. */
@Getter
@Builder
@Jacksonized
public class ProofChallengeRequest {

    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{43}")
    private final String keyThumbprint;

    @Size(max = JwtRefreshTokenService.MAX_TOKEN_LENGTH)
    @Pattern(regexp = JwtRefreshTokenService.TOKEN_PATTERN)
    private final String refreshToken;
}

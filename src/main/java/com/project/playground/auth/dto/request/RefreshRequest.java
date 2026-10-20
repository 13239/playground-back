package com.project.playground.auth.dto.request;

import com.project.playground.global.security.jwt.JwtRefreshTokenService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 갱신 입력은 Builder로 복원하며 토큰의 진위와 세대는 서비스에서 서명 검증 후 판단한다. */
@Getter
@Builder
@Jacksonized
public class RefreshRequest {

    @NotBlank(message = "Refresh Token은 필수입니다.")
    @Size(max = JwtRefreshTokenService.MAX_TOKEN_LENGTH, message = "Refresh Token이 너무 깁니다.")
    @Pattern(regexp = JwtRefreshTokenService.TOKEN_PATTERN, message = "Refresh Token 형식이 올바르지 않습니다.")
    private final String refreshToken;

    // BFF가 쿠키 암호화 키와 기존 Refresh Token으로 만든 동일 갱신 식별자. 브라우저 입력을 전달하지 않는다.
    @NotBlank(message = "갱신 요청 식별자는 필수입니다.")
    @Pattern(regexp = "[a-f0-9]{64}", message = "갱신 요청 식별자 형식이 올바르지 않습니다.")
    private final String requestId;
}

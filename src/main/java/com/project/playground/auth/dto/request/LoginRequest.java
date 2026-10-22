package com.project.playground.auth.dto.request;

import com.project.playground.global.validation.StringValid;
import com.project.playground.global.security.jwt.JwtRefreshTokenService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 로그인 입력은 Builder로 복원하며 기존 브라우저의 세션 교체 증명은 선택적으로 받는다. */
@Getter
@Builder
@Jacksonized
public class LoginRequest {

    @StringValid(label = "아이디", required = true, max = 50)
    private final String username;

    @StringValid(label = "비밀번호", required = true, max = 72)
    private final String password;

    @NotNull(message = "로그인 유지 여부는 필수입니다.")
    private final Boolean keepLoggedIn;

    // 첫 시도는 기존 로그인을 유지한다. 사용자가 안내를 확인한 재요청에서만 다른 세션을 종료한다.
    @Builder.Default
    @NotNull(message = "기존 로그인 교체 여부를 확인해 주세요.")
    private final Boolean replaceExistingSession = false;

    // BFF가 기존 암호화 쿠키에서 읽은 교체 증명. 없거나 이미 종료된 세션이면 신규 로그인으로 처리한다.
    @Size(max = JwtRefreshTokenService.MAX_TOKEN_LENGTH, message = "기존 세션 교체 증명이 너무 깁니다.")
    @Pattern(regexp = JwtRefreshTokenService.TOKEN_PATTERN, message = "기존 세션 교체 증명 형식이 올바르지 않습니다.")
    private final String previousRefreshToken;

    // 로그인과 갱신이 겹치면 기존 60초 갱신 결과를 교체 증명으로 확인한다. BFF가 서버에서 생성한다.
    @Pattern(regexp = "[a-f0-9]{64}", message = "기존 갱신 식별자 형식이 올바르지 않습니다.")
    private final String previousRefreshRequestId;
}

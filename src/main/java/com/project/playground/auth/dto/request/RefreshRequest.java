package com.project.playground.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefreshRequest {

    @NotBlank(message = "Refresh Token은 필수입니다.")
    @Pattern(regexp = "[A-Za-z0-9_-]{64}", message = "Refresh Token 형식이 올바르지 않습니다.")
    private String refreshToken;

    // BFF가 쿠키 암호화 키와 기존 Refresh Token으로 만든 동일 갱신 식별자. 브라우저 입력을 전달하지 않는다.
    @NotBlank(message = "갱신 요청 식별자는 필수입니다.")
    @Pattern(regexp = "[a-f0-9]{64}", message = "갱신 요청 식별자 형식이 올바르지 않습니다.")
    private String requestId;
}

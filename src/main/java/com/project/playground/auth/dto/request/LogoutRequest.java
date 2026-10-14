package com.project.playground.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LogoutRequest {

    @NotBlank(message = "Refresh Token은 필수입니다.")
    @Pattern(regexp = "[A-Za-z0-9_-]{64}", message = "Refresh Token 형식이 올바르지 않습니다.")
    private String refreshToken;
}

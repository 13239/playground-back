package com.project.playground.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RefreshRequest {

    @NotBlank(message = "Refresh Token은 필수입니다.")
    private String refreshToken;
}

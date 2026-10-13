package com.project.playground.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {

    private String accessToken;

    private String refreshToken;

    private String tokenType;

    private long expiresIn;
}

package com.project.playground.auth.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class SessionActivityResponse {

    private boolean keepLoggedIn;

    private Instant expiresAt;
}

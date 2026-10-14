package com.project.playground.global.security.session;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class Session {

    private final UUID sessionId;

    private final Long id;

    private final String username;

    private final boolean keepLoggedIn;
}

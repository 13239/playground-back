package com.project.playground.domain.user.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserStatus {

    ACTIVE("활성"),
    INACTIVE("비활성"),
    PENDING("가입대기");

    private final String description;
}

package com.project.playground.domain.usermanagement.dto;

import lombok.*;
import java.util.List;

/** 회원 검색·배정용 이름/코드만 공개하며 기능 권한·역할 설명·회원 수는 제공하지 않는다. */
@Getter @Builder
public class UserRoleOptions {
    private final List<Option> items;
    private final String nextCursor;
    @Getter @Builder
    public static class Option { private final String code; private final String name; }
}

package com.project.playground.domain.usermanagement.dto;

import lombok.*;

/** 기본 상세는 회원 정보만 반환한다. 개인정보는 별도의 기능 권한과 감사 경로에서 원문으로 조회한다. */
@Getter @Builder
public class AdminUserDetails {
    private final AdminUserSummary user;
}

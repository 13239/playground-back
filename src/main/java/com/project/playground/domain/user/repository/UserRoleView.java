package com.project.playground.domain.user.repository;

/** 관리자 인증 이후 사용하는 최소 권한 정보 전용 DB 프로젝션이다. */
public interface UserRoleView {
    Long getUserId();
    String getRoleCode();
    long getRoleVersion();
}

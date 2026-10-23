package com.project.playground.domain.authorization.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 기능 권한은 구현과 함께 추가하며, 역할 코드는 DB에서 제한 없이 확장한다. */
@Getter
@RequiredArgsConstructor
public enum PermissionCode {
    ADMIN_ACCESS("관리자 페이지 접근", "ACCESS", false),
    DASHBOARD_READ("운영 대시보드 조회", "DASHBOARD", false),
    USER_READ("사용자 조회", "USER", false),
    USER_STATUS_UPDATE("계정 제한·해제", "USER", false),
    USER_SESSION_REVOKE("사용자 세션 종료", "USER", false),
    USER_SENSITIVE_READ("사용자 개인정보 열람", "USER", false),
    USER_ROLE_UPDATE("회원 역할 변경", "AUTHORIZATION", true),
    ROLE_READ("역할·권한 조회", "AUTHORIZATION", true),
    ROLE_WRITE("역할 추가·수정·삭제", "AUTHORIZATION", true),
    ROLE_PERMISSION_UPDATE("역할별 기능 권한 변경", "AUTHORIZATION", true),
    ACCESS_POLICY_READ("접속 제한 설정 조회", "OPERATIONS", false),
    ACCESS_POLICY_UPDATE("접속 제한·안내 문구 변경", "OPERATIONS", false),
    ACCESS_EXEMPTION_UPDATE("접속 허용 역할 지정", "OPERATIONS", true),
    CHAT_HISTORY_READ("채팅 내역 조회", "CHAT", false),
    ACTIVITY_LOG_READ("사용자 활동 로그 조회", "LOG", false),
    ADMIN_AUDIT_READ("관리자 작업 기록 조회", "LOG", false),
    GAME_READ("게임 목록·설정 조회", "GAME", false),
    GAME_WRITE("게임 정보·장르·모드 변경", "GAME", false),
    GAME_AVAILABILITY_UPDATE("게임 공개·점검 설정", "GAME", false),
    SERVICE_STATUS_READ("서비스 상태 조회", "OPERATIONS", false);

    private final String displayName;
    private final String category;
    private final boolean superAdminOnly;
}


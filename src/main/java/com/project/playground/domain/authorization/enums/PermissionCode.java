package com.project.playground.domain.authorization.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

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

    /** 화면 진입과 실제 API 호출에 함께 필요한 권한을 한 사전에서 제공한다. */
    public Set<PermissionCode> getRequiredPermissions() {
        return switch (this) {
            case ADMIN_ACCESS -> Set.of();
            case USER_STATUS_UPDATE, USER_SESSION_REVOKE, USER_SENSITIVE_READ, USER_ROLE_UPDATE -> Set.of(ADMIN_ACCESS, USER_READ);
            case ROLE_WRITE -> Set.of(ADMIN_ACCESS, ROLE_READ);
            case ROLE_PERMISSION_UPDATE -> Set.of(ADMIN_ACCESS, ROLE_READ, ROLE_WRITE);
            case ACCESS_POLICY_UPDATE, ACCESS_EXEMPTION_UPDATE -> Set.of(ADMIN_ACCESS, ACCESS_POLICY_READ);
            case GAME_WRITE, GAME_AVAILABILITY_UPDATE -> Set.of(ADMIN_ACCESS, GAME_READ);
            default -> Set.of(ADMIN_ACCESS);
        };
    }

    /** 각 선택 항목이 실제로 허용하는 작업을 상세 화면에 설명한다. */
    public String getDescription() {
        return switch (this) {
            case ADMIN_ACCESS -> "관리자 페이지에 접속할 수 있어요.";
            case DASHBOARD_READ -> "가입·접속 현황과 운영 지표를 확인할 수 있어요.";
            case USER_READ -> "회원 목록과 기본 정보, 닉네임 이력을 조회할 수 있어요.";
            case USER_STATUS_UPDATE -> "회원 계정을 제한하거나 다시 활성화할 수 있어요.";
            case USER_SESSION_REVOKE -> "회원의 모든 로그인 세션을 종료할 수 있어요.";
            case USER_SENSITIVE_READ -> "이메일·연락처·생년월일 등 개인정보를 열람할 수 있어요.";
            case USER_ROLE_UPDATE -> "회원에게 다른 역할을 배정할 수 있어요.";
            case ROLE_READ -> "역할 목록과 역할별 기능 설정을 조회할 수 있어요.";
            case ROLE_WRITE -> "역할을 추가하거나 이름·설명을 수정하고 삭제할 수 있어요.";
            case ROLE_PERMISSION_UPDATE -> "역할에서 사용할 수 있는 기능을 변경할 수 있어요.";
            case ACCESS_POLICY_READ -> "사이트 접속 제한과 허용 역할 설정을 확인할 수 있어요.";
            case ACCESS_POLICY_UPDATE -> "사이트 접속 제한과 안내 문구를 변경할 수 있어요.";
            case ACCESS_EXEMPTION_UPDATE -> "접속 제한 중에도 이용할 수 있는 역할을 지정할 수 있어요.";
            case CHAT_HISTORY_READ -> "공용 채팅과 게임방의 저장된 메시지를 조회할 수 있어요.";
            case ACTIVITY_LOG_READ -> "로그인·가입 등 사용자 활동 기록을 조회할 수 있어요.";
            case ADMIN_AUDIT_READ -> "관리자의 설정 변경과 중요 작업 기록을 확인할 수 있어요.";
            case GAME_READ -> "게임·플레이 모드·필터의 정보와 이용 상태를 확인할 수 있어요.";
            case GAME_WRITE -> "게임 정보·플레이 모드·필터와 표시 순서를 관리할 수 있어요.";
            case GAME_AVAILABILITY_UPDATE -> "게임과 플레이 모드의 공개·점검·이용 중지 상태를 변경할 수 있어요.";
            case SERVICE_STATUS_READ -> "로그인·게임·채팅 서비스의 상태를 확인할 수 있어요.";
        };
    }
}


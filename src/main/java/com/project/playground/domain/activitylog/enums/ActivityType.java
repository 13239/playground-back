package com.project.playground.domain.activitylog.enums;

/** 기록할 활동 종류다. 새 활동은 상수만 추가하며 DB 스키마는 변경하지 않는다. */
public enum ActivityType {

    // 로그인 (비밀번호 인증과 세션 생성까지 완료해야 성공)
    LOGIN,

    // 회원가입
    SIGN_UP,

    // 현재 비밀번호 확인과 계정 탈퇴 상태 변경을 완료한 본인 요청
    WITHDRAWAL
}

package com.project.playground.domain.activitylog.enums;

/** 관리자 화면에서 활동 종류를 묶어 조회하기 위한 분류다. DB에는 저장하지 않고 활동 종류에서 파생한다. */
public enum ActivityCategory {

    // 로그인·로그아웃 등 인증 활동
    AUTH,

    // 회원가입·회원 정보 변경 등 회원 활동
    USER
}

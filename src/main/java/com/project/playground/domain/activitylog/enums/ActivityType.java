package com.project.playground.domain.activitylog.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 기록할 활동 종류다. 새 활동은 상수만 추가하며 DB 스키마는 변경하지 않는다. */
@Getter
@RequiredArgsConstructor
public enum ActivityType {

    // 로그인 (비밀번호 인증과 세션 생성까지 완료해야 성공)
    LOGIN(ActivityCategory.AUTH),

    // 회원가입
    SIGN_UP(ActivityCategory.USER);

    private final ActivityCategory category;
}

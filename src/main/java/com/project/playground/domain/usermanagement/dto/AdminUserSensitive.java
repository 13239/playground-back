package com.project.playground.domain.usermanagement.dto;

import com.project.playground.domain.user.enums.Gender;
import lombok.*;
import java.time.LocalDate;

/** 허용된 관리자에게 바로 표시할 한 회원의 개인정보 원문만 담는다. 비밀번호·토큰은 조회하지 않는다. */
@Getter @Builder
public class AdminUserSensitive {
    private final Long userId;
    private final String email;
    private final String phone;
    private final LocalDate birthday;
    private final Gender gender;
    private final long managementVersion;
}

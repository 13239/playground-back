package com.project.playground.domain.user.dto.request;

import com.project.playground.global.validation.StringValid;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 비밀번호 원문은 변경 요청에서만 사용하고 응답·로그·DTO 문자열에 포함하지 않는다. */
@Getter
@Builder
@Jacksonized
public class ChangePasswordRequest {

    @StringValid(label = "현재 비밀번호", required = true, max = 72)
    private final String currentPassword;

    @StringValid(label = "새 비밀번호", required = true, min = 6, max = 72)
    private final String newPassword;
}

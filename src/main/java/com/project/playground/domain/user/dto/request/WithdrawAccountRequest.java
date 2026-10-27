package com.project.playground.domain.user.dto.request;

import com.project.playground.global.validation.StringValid;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 본인 확인용 비밀번호만 받으며 회원 ID·계정 상태·삭제 시각은 입력으로 받지 않는다. */
@Getter
@Builder
@Jacksonized
public class WithdrawAccountRequest {
    @StringValid(label = "현재 비밀번호", required = true, max = 72)
    private final String currentPassword;
}

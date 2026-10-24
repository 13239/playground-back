package com.project.playground.domain.user.dto.request;

import com.project.playground.global.validation.StringValid;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 가입과 같은 닉네임 정책을 적용하고 아바타 변경과 별도로 저장한다. */
@Getter
@Builder
@Jacksonized
public class UpdateNicknameRequest {

    @StringValid(label = "닉네임", required = true, max = 50)
    private final String nickname;
}

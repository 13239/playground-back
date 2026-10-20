package com.project.playground.domain.user.dto.request;

import com.project.playground.domain.user.enums.ProfileAvatar;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 본인 프로필의 제공된 캐릭터만 변경하며 회원 ID·이미지 URL·파일은 요청에서 받지 않는다. */
@Getter
@Builder
@Jacksonized
public class UpdateProfileRequest {

    @NotNull(message = "프로필 이미지를 선택해 주세요.")
    private final ProfileAvatar avatarId;
}

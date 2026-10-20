package com.project.playground.domain.user.dto.response;

import com.project.playground.domain.user.entity.User;
import com.project.playground.domain.user.enums.ProfileAvatar;
import lombok.Builder;
import lombok.Getter;

/** 화면에 필요한 닉네임과 고정 캐릭터 식별자만 제공하며 인증 정보나 선택 개인정보는 포함하지 않는다. */
@Getter
@Builder
public class UserProfileResponse {

    private final String nickname;
    private final ProfileAvatar avatarId;

    public static UserProfileResponse from(User user) {
        return UserProfileResponse.builder()
                .nickname(user.getNickname())
                .avatarId(user.getProfileAvatar())
                .build();
    }
}

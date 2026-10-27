package com.project.playground.domain.user.dto.response;

import com.project.playground.domain.user.entity.User;
import com.project.playground.domain.user.enums.ProfileAvatar;
import lombok.Builder;
import lombok.Getter;

/** 닉네임·캐릭터·친구용 공개 키만 제공하며 인증 정보나 선택 개인정보는 포함하지 않는다. */
@Getter
@Builder
public class UserProfileResponse {

    private final String nickname;
    // 내부 계정 ID와 분리한 공개 키만 채팅 프로필에 동기화한다.
    private final String publicUserId;
    private final ProfileAvatar avatarId;

    public static UserProfileResponse from(User user) {
        return UserProfileResponse.builder()
                .nickname(user.getNickname())
                .publicUserId(user.getPublicId())
                .avatarId(user.getProfileAvatar())
                .build();
    }
}

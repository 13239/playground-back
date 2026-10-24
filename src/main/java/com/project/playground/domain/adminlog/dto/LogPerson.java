package com.project.playground.domain.adminlog.dto;

import com.project.playground.domain.user.enums.ProfileAvatar;
import lombok.Builder;
import lombok.Getter;

/** 기록의 회원 번호는 탈퇴·삭제 후에도 보존하고 표시용 공개 프로필만 함께 제공한다. */
@Getter @Builder
public class LogPerson {
    private final Long id;
    private final String username;
    private final String nickname;
    private final ProfileAvatar avatarId;
}

package com.project.playground.domain.usermanagement.dto;

import com.project.playground.domain.user.enums.*;
import lombok.*;
import java.time.LocalDateTime;

/** 목록에는 인증 정보나 연락처를 포함하지 않는다. 버전은 실제 편집 대상의 식별에 사용한다. */
@Getter @Builder
public class AdminUserSummary {
    private final Long id;
    private final String username;
    private final String nickname;
    private final ProfileAvatar avatarId;
    private final UserStatus status;
    private final boolean deleted;
    private final String roleCode;
    private final String roleName;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final long managementVersion;
    private final long roleVersion;
}

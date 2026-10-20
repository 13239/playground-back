package com.project.playground.domain.gomoku.dto.response;

import com.project.playground.domain.gomoku.enums.*;
import com.project.playground.domain.user.enums.ProfileAvatar;
import lombok.Builder;
import lombok.Getter;

/** 방 목록에는 표시용 정보만 제공한다. 초대 코드·비밀번호 해시·회원 아이디는 포함하지 않는다. */
@Getter
@Builder
public class RoomSummaryResponse {
    private final Long id;
    private final String title;
    private final String hostNickname;
    private final ProfileAvatar hostAvatarId;
    private final RoomVisibility visibility;
    private final int playerCount;
    private final int undoLimit;
    private final ForbiddenRule forbiddenRule;
    private final RoomStatus status;
    private final boolean owned;
}

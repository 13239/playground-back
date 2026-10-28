package com.project.playground.domain.gomoku.dto.response;

import com.project.playground.domain.gomoku.entity.GomokuRoomInvitation;
import com.project.playground.domain.gomoku.enums.ForbiddenRule;
import com.project.playground.domain.gomoku.enums.RoomVisibility;
import com.project.playground.domain.social.SocialDtos;
import com.project.playground.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;
import java.time.Instant;

/** 비밀번호·방 코드·내부 회원 번호 없이 수신자가 확인할 규칙과 공개 프로필만 제공한다. */
@Getter @Builder
public class GomokuInvitationResponse {
    private final String id;
    private final Long roomId;
    private final String roomTitle;
    private final RoomVisibility visibility;
    private final int undoLimit;
    private final ForbiddenRule forbiddenRule;
    private final SocialDtos.Person inviter;
    private final SocialDtos.Person target;
    private final Instant expiresAt;

    public static GomokuInvitationResponse from(GomokuRoomInvitation invitation) {
        var room = invitation.getRoom();
        return GomokuInvitationResponse.builder().id(invitation.getPublicId()).roomId(room.getId())
                .roomTitle(room.getTitle()).visibility(room.getVisibility()).undoLimit(room.getUndoLimit())
                .forbiddenRule(room.getForbiddenRule()).inviter(person(invitation.getInviter()))
                .target(person(invitation.getTarget())).expiresAt(invitation.getExpiresAt()).build();
    }

    private static SocialDtos.Person person(User user) {
        return SocialDtos.Person.builder().publicId(user.getPublicId()).nickname(user.getNickname())
                .avatarId(user.getProfileAvatar().name()).relation("FRIEND").build();
    }

    /** 중복 응답은 같은 참가에만 복귀시킨다. 나간 과거 초대로 다른 방 이동을 유발하지 않는다. */
    @Getter @Builder
    public static class Decision {
        private final GomokuRoomInvitation.State state;
        private final Long roomId;
    }
}

package com.project.playground.domain.gomoku.dto.response;

import com.project.playground.domain.gomoku.entity.GomokuRoom;
import com.project.playground.domain.gomoku.entity.GomokuRoomParticipant;
import com.project.playground.domain.gomoku.enums.ForbiddenRule;
import com.project.playground.domain.gomoku.enums.RoomVisibility;
import com.project.playground.domain.user.enums.ProfileAvatar;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 참가자에게만 코드와 준비 상태를 제공한다. 고정 규칙은 서버 상수이며 흑백·대국 상태를 미리 만들지 않는다. */
@Getter @Builder
public class WaitingRoomResponse {
    private final Long id;
    private final String title;
    private final String inviteCode;
    private final RoomVisibility visibility;
    private final int undoLimit;
    private final ForbiddenRule forbiddenRule;
    private final int boardSize;
    private final int turnSeconds;
    private final long revision;
    private final String membershipId;
    private final boolean allReady;
    private final List<Participant> participants;

    public static WaitingRoomResponse from(GomokuRoom room, List<GomokuRoomParticipant> members, Long viewer) {
        return WaitingRoomResponse.builder().id(room.getId()).title(room.getTitle()).inviteCode(room.getInviteCode())
                .visibility(room.getVisibility()).undoLimit(room.getUndoLimit()).forbiddenRule(room.getForbiddenRule())
                .boardSize(20).turnSeconds(30).revision(room.getRevision())
                .membershipId(members.stream().filter(p -> p.getUser().getId().equals(viewer))
                        .findFirst().orElseThrow().getMembershipId())
                .allReady(members.size() == 2 && members.stream().allMatch(GomokuRoomParticipant::isReady))
                .participants(members.stream().map(p -> Participant.builder().seat(p.getSeat())
                        .nickname(p.getUser().getNickname()).avatarId(p.getUser().getProfileAvatar())
                        .host(p.getUser().getId().equals(room.getHost().getId())).self(p.getUser().getId().equals(viewer))
                        .ready(p.isReady()).build()).toList()).build();
    }

    @Getter @Builder
    public static class Participant {
        private final int seat;
        private final String nickname;
        private final ProfileAvatar avatarId;
        private final boolean host;
        private final boolean self;
        private final boolean ready;
    }
}

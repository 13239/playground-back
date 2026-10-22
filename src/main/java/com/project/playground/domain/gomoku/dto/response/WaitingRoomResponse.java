package com.project.playground.domain.gomoku.dto.response;

import com.project.playground.domain.gomoku.entity.GomokuRoom;
import com.project.playground.domain.gomoku.entity.GomokuRoomParticipant;
import com.project.playground.domain.gomoku.entity.GomokuMatch;
import com.project.playground.domain.gomoku.enums.RoomStatus;
import com.project.playground.domain.gomoku.enums.ForbiddenRule;
import com.project.playground.domain.gomoku.enums.RoomVisibility;
import com.project.playground.domain.user.enums.ProfileAvatar;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.time.Instant;

/** 참가자에게만 코드와 준비 상태를 제공한다. 고정 규칙은 서버 상수이며 실제 시작한 대국만 공개한다. */
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
    // 서버 시각·방 상태와 검증된 대국 DTO만 추가하고 내부 참가 식별자는 기존 본인 값만 제공한다.
    private final RoomStatus status;
    private final Instant serverTime;
    private final MatchResponse game;

    public static WaitingRoomResponse from(GomokuRoom room, List<GomokuRoomParticipant> members, Long viewer) {
        return from(room, members, viewer, null, Instant.now());
    }

    public static WaitingRoomResponse from(GomokuRoom room, List<GomokuRoomParticipant> members, Long viewer, GomokuMatch game, Instant now) {
        return WaitingRoomResponse.builder().id(room.getId()).title(room.getTitle()).inviteCode(room.getInviteCode())
                .status(room.getStatus()).serverTime(now).game(game == null ? null : MatchResponse.from(game, now))
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

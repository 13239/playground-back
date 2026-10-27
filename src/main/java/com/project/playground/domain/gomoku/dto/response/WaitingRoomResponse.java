package com.project.playground.domain.gomoku.dto.response;

import com.project.playground.domain.gomoku.entity.GomokuRoom;
import com.project.playground.domain.gomoku.entity.GomokuRoomParticipant;
import com.project.playground.domain.gomoku.entity.GomokuMatch;
import com.project.playground.domain.gomoku.entity.GomokuPlayerStats;
import com.project.playground.domain.gomoku.enums.RoomStatus;
import com.project.playground.domain.gomoku.enums.ForbiddenRule;
import com.project.playground.domain.gomoku.enums.RoomVisibility;
import com.project.playground.domain.user.enums.ProfileAvatar;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.time.Instant;

/** 방장에게만 초대 코드를 제공한다. 고정 규칙은 서버 상수이며 실제 시작한 대국만 공개한다. */
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

    /** 회원 ID를 응답에 노출하지 않고 닉네임별 공개 전적만 참가 카드에 포함한다. */
    public static WaitingRoomResponse from(GomokuRoom room, List<GomokuRoomParticipant> members, Long viewer,
                                           GomokuMatch game, Instant now, Map<Long, GomokuPlayerStats> stats) {
        // 일반 참가자에게는 JSON 응답에서도 초대 코드를 전달하지 않는다. 방장 위임은 현재 방장 ID를 기준으로 즉시 반영한다.
        return WaitingRoomResponse.builder().id(room.getId()).title(room.getTitle())
                .inviteCode(room.getHost().getId().equals(viewer) ? room.getInviteCode() : null)
                .status(room.getStatus()).serverTime(now).game(game == null ? null : MatchResponse.from(game, now))
                .visibility(room.getVisibility()).undoLimit(room.getUndoLimit()).forbiddenRule(room.getForbiddenRule())
                .boardSize(20).turnSeconds(30).revision(room.getRevision())
                .membershipId(members.stream().filter(p -> p.getUser().getId().equals(viewer))
                        .findFirst().orElseThrow().getMembershipId())
                .allReady(members.size() == 2 && members.stream().allMatch(GomokuRoomParticipant::isReady))
                .participants(members.stream().map(p -> participant(p, viewer, room.getHost().getId(), stats)).toList())
                .build();
    }

    /** 같은 회원의 전적을 한 번만 찾아 중복 Map 조회 없이 DTO에 필요한 공개 값만 복사한다. */
    private static Participant participant(GomokuRoomParticipant member, Long viewer, Long hostId,
                                           Map<Long, GomokuPlayerStats> stats) {
        var user = member.getUser();
        var aggregate = stats.get(user.getId());
        return Participant.builder()
                .seat(member.getSeat()).nickname(user.getNickname()).avatarId(user.getProfileAvatar())
                .publicUserId(user.getPublicId())
                .host(user.getId().equals(hostId)).self(user.getId().equals(viewer)).ready(member.isReady())
                // 전적이 없는 회원은 0승 0패 0무로 표시하고 내부 회원 ID는 전송하지 않는다.
                .wins(aggregate == null ? 0 : aggregate.getWins())
                .losses(aggregate == null ? 0 : aggregate.getLosses())
                .draws(aggregate == null ? 0 : aggregate.getDraws())
                .build();
    }

    @Getter @Builder
    public static class Participant {
        private final int seat;
        private final String nickname;
        // 함께한 게임에서 공통 프로필 팝업을 열기 위한 공개 식별자다.
        private final String publicUserId;
        private final ProfileAvatar avatarId;
        private final boolean host;
        private final boolean self;
        private final boolean ready;
        // 서버에 확정 저장된 온라인 대국의 누적값이다. 무승부는 승률 분모에서 제외한다.
        private final long wins;
        private final long losses;
        private final long draws;
    }
}

package com.project.playground.domain.gomoku.dto.response;

import com.project.playground.domain.gomoku.entity.GomokuRoom;
import lombok.Builder;
import lombok.Getter;

/** 본인이 만든 방에만 초대 코드를 제공하며 비밀번호 원문·해시는 어떤 응답에도 포함하지 않는다. */
@Getter
@Builder
public class HostedRoomResponse {
    private final Long id;
    private final String title;
    private final String inviteCode;

    public static HostedRoomResponse from(GomokuRoom room) {
        return HostedRoomResponse.builder().id(room.getId()).title(room.getTitle()).inviteCode(room.getInviteCode()).build();
    }
}

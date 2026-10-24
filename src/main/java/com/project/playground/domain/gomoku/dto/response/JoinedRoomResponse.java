package com.project.playground.domain.gomoku.dto.response;

import com.project.playground.domain.gomoku.entity.GomokuRoom;
import lombok.Builder;
import lombok.Getter;

/** 방장이 아닌 참가자의 목록 복귀 링크에는 코드 없이 방 식별자와 제목만 제공한다. */
@Getter @Builder
public class JoinedRoomResponse {
    private final Long id;
    private final String title;

    public static JoinedRoomResponse from(GomokuRoom room) {
        return JoinedRoomResponse.builder().id(room.getId()).title(room.getTitle()).build();
    }
}

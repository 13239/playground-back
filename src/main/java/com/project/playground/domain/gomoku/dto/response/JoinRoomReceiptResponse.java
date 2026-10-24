package com.project.playground.domain.gomoku.dto.response;

import lombok.Builder;
import lombok.Getter;

/** 입장이 커밋되면 최소 방 식별자만 응답하고, 상세 대기실 상태는 화면 전환 후 별도로 조회한다. */
@Getter
@Builder
public class JoinRoomReceiptResponse {
    private final Long roomId;
}

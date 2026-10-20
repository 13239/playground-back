package com.project.playground.domain.gomoku.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 닫을 방 번호만 수신하고 소유자 여부와 빈 대기방 상태는 트랜잭션 안에서 확인한다. */
@Getter
@Builder
@Jacksonized
public class CloseRoomRequest {
    @NotNull
    @Positive
    private final Long roomId;
}

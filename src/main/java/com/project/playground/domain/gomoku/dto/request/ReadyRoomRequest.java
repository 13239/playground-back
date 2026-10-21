package com.project.playground.domain.gomoku.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 준비와 준비 취소는 현재 참가자 본인의 상태만 변경한다. */
@Getter @Builder @Jacksonized
public class ReadyRoomRequest {
    @NotNull @Positive private final Long roomId;
    @NotNull private final Boolean ready;
}

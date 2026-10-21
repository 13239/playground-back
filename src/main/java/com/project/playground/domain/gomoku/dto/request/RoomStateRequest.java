package com.project.playground.domain.gomoku.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 본인의 참가 권한은 인증 주체에서 확인하고 클라이언트가 보낸 사용자 ID는 받지 않는다. */
@Getter @Builder @Jacksonized
public class RoomStateRequest {
    @NotNull @Positive private final Long roomId;
}

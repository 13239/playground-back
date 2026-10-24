package com.project.playground.domain.gomoku.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 방장 본인의 페이지와 최신 방 순서로 선택한 상대 좌석만 강퇴하고 재입장 정책을 필수로 받는다. 회원 ID는 요청받지 않는다. */
@Getter @Builder @Jacksonized
public class KickRoomRequest {
    @NotNull @Positive private final Long roomId;
    @NotNull @Min(1) @Max(2) private final Integer seat;
    @NotNull @PositiveOrZero private final Long revision;
    // true는 동일 방 재입장 금지, false는 재입장 허용이다. 누락된 요청은 검증에서 거부한다.
    @NotNull private final Boolean blockRejoin;
    @NotNull @Pattern(regexp = "[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")
    private final String pageId;
}

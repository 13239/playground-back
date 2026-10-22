package com.project.playground.domain.gomoku.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 준비와 준비 취소는 현재 참가자 본인의 상태만 변경한다. */
@Getter @Builder @Jacksonized
public class ReadyRoomRequest {
    @NotNull @Positive private final Long roomId;
    @NotNull private final Boolean ready;
    // 서버가 현재 탭의 문서에서 보낸 준비 요청인지 확인한다.
    @NotNull @Pattern(regexp = "[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}") private final String pageId;
}

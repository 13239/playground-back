package com.project.playground.domain.gomoku.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 인증된 본인의 참가 화면만 열거나 종료한다. CLOSE는 새로고침 유예, LEAVE는 뒤로가기의 즉시 퇴장이다. */
@Getter @Builder @Jacksonized
public class RoomPageRequest {
    public enum Action { OPEN, CLOSE, LEAVE }
    @NotNull @Positive private final Long roomId;
    @NotNull @Pattern(regexp = "[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")
    private final String membershipId;
    @NotNull @Pattern(regexp = "[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")
    private final String pageId;
    @NotNull private final Action action;
}

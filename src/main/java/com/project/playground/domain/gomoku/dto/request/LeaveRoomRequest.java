package com.project.playground.domain.gomoku.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 늦은 이전 화면의 퇴장이 같은 방에 다시 입장한 참가자를 삭제하지 못하도록 참가 식별자를 함께 검증한다. */
@Getter @Builder @Jacksonized
public class LeaveRoomRequest {
    @NotNull @Positive private final Long roomId;
    @NotNull @Pattern(regexp = "[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")
    private final String membershipId;
    // 다른 탭/이전 문서의 명시적 나가기 요청은 현재 참가를 종료하지 못한다.
    @NotNull @Pattern(regexp = "[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")
    private final String pageId;
}

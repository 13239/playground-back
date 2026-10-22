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
    // 기존 DB 이전의 UUID(v1)와 신규 참가 UUID(v4)를 모두 받아 조회 성공 후 OPEN이 거절되지 않게 한다.
    @NotNull @Pattern(regexp = "[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")
    private final String membershipId;
    @NotNull @Pattern(regexp = "[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")
    private final String pageId;
    // 탭 단위 식별자는 새로고침 사이에 유지하되 문서마다 별도 pageId로 종료 신호를 구분한다.
    @NotNull @Pattern(regexp = "[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")
    private final String tabId;
    @NotNull private final Action action;
}

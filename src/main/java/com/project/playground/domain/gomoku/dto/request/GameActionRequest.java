package com.project.playground.domain.gomoku.dto.request;

import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 현재 대국·방 순서·요청 UUID를 결합해 늦은 착수와 중복 무르기를 거부한다. 사용자·돌 색은 입력받지 않는다. */
@Getter @Builder @Jacksonized
public class GameActionRequest {
    public enum Action { PICK, MOVE, UNDO_REQUEST, UNDO_REPLY, RESIGN, DISCONNECT, REMATCH }
    @NotNull @Positive private final Long roomId;
    @NotNull @Positive private final Long matchId;
    @NotNull @PositiveOrZero private final Long revision;
    @NotNull @Pattern(regexp = "[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}") private final String requestId;
    @NotNull private final Action action;
    @Min(0) @Max(19) private final Short x;
    @Min(0) @Max(19) private final Short y;
    @Min(0) @Max(1) private final Short card;
    private final Boolean accept;

    @AssertTrue(message = "대국 동작에 필요한 값만 전달해 주세요.")
    public boolean isValidAction() {
        if (action == null) return true;
        return switch (action) {
            case MOVE -> x != null && y != null && card == null && accept == null;
            case PICK -> card != null && x == null && y == null && accept == null;
            case UNDO_REPLY -> accept != null && x == null && y == null && card == null;
            default -> x == null && y == null && card == null && accept == null;
        };
    }
    public String fingerprint() { return action + ":" + x + ":" + y + ":" + card + ":" + accept; }
}

package com.project.playground.domain.gomoku.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 본인의 참가 권한은 인증 주체에서 확인하고 클라이언트가 보낸 사용자 ID는 받지 않는다. */
@Getter @Builder @Jacksonized
public class RoomStateRequest {
    @NotNull @Positive private final Long roomId;
    // 최초 방 조회는 소유권 확정 전이므로 조회 전용이며 연결 후에는 현재 페이지 UUID를 전달한다.
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
    private final String pageId;
}

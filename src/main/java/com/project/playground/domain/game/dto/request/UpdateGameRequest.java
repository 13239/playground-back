package com.project.playground.domain.game.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 조회한 게임 버전으로 오래된 표시 정보 덮어쓰기를 거절한다. */
@Getter @Builder @Jacksonized
public class UpdateGameRequest {
    @NotNull @Valid private final GameMetadataRequest metadata;
    @NotNull @PositiveOrZero private final Long expectedVersion;
    // 변경 사유 입력 없이 변경 전후 값을 자동 감사 기록으로 남긴다.
}

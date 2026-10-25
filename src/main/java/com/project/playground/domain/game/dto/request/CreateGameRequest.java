package com.project.playground.domain.game.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 배포된 게임 코드만 숨김·이용 중지 상태로 최초 등록한다. */
@Getter @Builder @Jacksonized
public class CreateGameRequest {
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") private final String code;
    @NotNull @Valid private final GameMetadataRequest metadata;
    // 작업자와 변경 값은 서버에서 자동 기록하며 별도의 변경 사유는 받지 않는다.
}

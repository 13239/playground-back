package com.project.playground.domain.game.dto.request;

import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 그래픽·접속·경쟁 방식은 등록된 구현에서 가져오고 인원만 지원 범위 안에서 설정한다. 변경 사유는 받지 않는다. */
@Getter @Builder @Jacksonized
public class GameModeRequest {
    @NotBlank @Size(max = 100) private final String name;
    @NotNull @Min(1) @Max(10000) private final Integer minHumanPlayers;
    @NotNull @Min(1) @Max(10000) private final Integer maxHumanPlayers;
    // 모드 정렬은 배포 구현의 정의 순서를 사용하므로 수정 요청에서 받지 않는다.
    @NotNull @PositiveOrZero private final Long expectedVersion;
}

package com.project.playground.domain.game.dto.request;

import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 장르 코드는 경로로 고정한다. 일반 수정은 이름·사용 여부만 받고 노출 순서는 그리드 API로 분리하며 변경 사유는 받지 않는다. */
@Getter @Builder @Jacksonized
public class GameGenreRequest {
    @NotBlank @Size(max = 50) private final String name;
    @NotNull private final Boolean enabled;
    @NotNull @PositiveOrZero private final Long expectedVersion;
}

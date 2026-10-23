package com.project.playground.domain.game.dto.request;

import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 장르 코드는 경로로 고정하며 일반 수정에서는 이름·노출 순서·사용 여부만 바꾼다. */
@Getter @Builder @Jacksonized
public class GameGenreRequest {
    @NotBlank @Size(max = 50) private final String name;
    @NotNull private final Boolean enabled;
    @NotNull @Min(0) @Max(1000000) private final Integer sortOrder;
    @NotNull @PositiveOrZero private final Long expectedVersion;
    @Size(max = 300) private final String reason;
}

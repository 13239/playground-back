package com.project.playground.domain.game.dto.request;

import com.project.playground.domain.game.enums.GameAvailability;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 숨김은 직접 주소의 신규 실행도 차단한다. 점검 문구는 평문으로 제한한다. */
@Getter @Builder @Jacksonized
public class GameAvailabilityRequest {
    @NotNull private final Boolean published;
    @NotNull private final GameAvailability availability;
    @NotNull @Size(max = 300) @Pattern(regexp = "[^\\p{Cc}\\p{Cf}]*") private final String message;
    @NotNull @PositiveOrZero private final Long expectedVersion;
    @Size(max = 300) private final String reason;
}

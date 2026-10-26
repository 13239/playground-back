package com.project.playground.domain.game.dto.request;

import com.project.playground.domain.game.enums.*;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 유형별 조건과 허용 아이콘만 수신하며 실행 코드·외부 이미지 주소는 받지 않는다. */
@Getter @Builder @Jacksonized
public class GameFilterRequest {
    @NotBlank @Size(max = 50) private final String name;
    @NotNull private final GameFilterType type;
    @NotBlank @Pattern(regexp = "gamepad|puzzle|car|swords|board|city|user|users|2d|cube|grid") private final String icon;
    @NotNull private final Boolean enabled;
    @Min(1) @Max(10000) private final Integer minPlayers;
    @Min(1) @Max(10000) private final Integer maxPlayers;
    private final GraphicsType graphicsType;
    @NotNull @PositiveOrZero private final Long expectedVersion;
    @AssertTrue(message = "필터 유형에 맞는 조건을 입력해 주세요.")
    public boolean isConfigurationValid() {
        if (type == null) return true;
        return switch (type) {
            case GENRE -> minPlayers == null && maxPlayers == null && graphicsType == null;
            case PLAYERS -> minPlayers != null && maxPlayers != null && minPlayers <= maxPlayers && graphicsType == null;
            case GRAPHICS -> graphicsType != null && minPlayers == null && maxPlayers == null;
        };
    }
}

package com.project.playground.domain.game.dto.request;

import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import java.util.Set;

/** 표시 정보에는 공개·점검 필드를 두지 않아 GAME_WRITE로 운영 권한을 우회하지 못하게 한다. */
@Getter @Builder @Jacksonized
public class GameMetadataRequest {
    @NotBlank @Size(max = 100) private final String name;
    @NotBlank @Size(max = 160) private final String summary;
    @NotBlank @Size(max = 2000) private final String description;
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") private final String thumbnailKey;
    @NotNull private final Set<@NotNull @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String> genreCodes;
    @NotNull @Min(0) @Max(1000000) private final Integer sortOrder;
}

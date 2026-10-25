package com.project.playground.domain.game.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** code 행을 beforeCode 행 바로 앞으로 이동한다. beforeCode가 null이면 전체 목록의 끝으로 이동한다. */
@Getter @Builder @Jacksonized
public class CatalogOrderMoveRequest {
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private final String code;

    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private final String beforeCode;
}

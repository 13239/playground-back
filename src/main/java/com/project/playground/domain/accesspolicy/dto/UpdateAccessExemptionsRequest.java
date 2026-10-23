package com.project.playground.domain.accesspolicy.dto;

import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import java.util.Set;

/** 역할 수를 네 개로 고정하지 않고 DB에 등록된 임의의 역할 코드를 선택한다. */
@Getter @Builder @Jacksonized
public class UpdateAccessExemptionsRequest {
    @NotNull private final Set<@NotNull @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String> roleCodes;
    @NotNull @PositiveOrZero private final Long expectedVersion;
    @Size(max = 300) private final String reason;
}

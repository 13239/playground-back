package com.project.playground.domain.authorization.dto.request;

import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 회원의 역할 버전으로 이전 화면의 늦은 변경 요청을 거절한다. */
@Getter
@Builder
@Jacksonized
public class ChangeUserRoleRequest {
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private final String roleCode;
    @NotNull @PositiveOrZero
    private final Long expectedVersion;
    @Size(max = 300)
    private final String reason;
}


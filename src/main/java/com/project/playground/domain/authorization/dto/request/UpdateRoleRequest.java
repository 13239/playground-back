package com.project.playground.domain.authorization.dto.request;

import com.project.playground.domain.authorization.enums.PermissionCode;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.util.Set;

/** 화면에서 읽은 버전과 기능 목록을 함께 보내 이름과 권한을 하나의 편집으로 저장한다. */
@Getter
@Builder
@Jacksonized
public class UpdateRoleRequest {
    @NotNull @PositiveOrZero
    private final Long expectedVersion;
    @NotBlank @Size(max = 50)
    private final String name;
    @Size(max = 255)
    private final String description;
    @NotNull
    private final Set<@NotNull PermissionCode> permissions;
    @Size(max = 300)
    private final String reason;
}


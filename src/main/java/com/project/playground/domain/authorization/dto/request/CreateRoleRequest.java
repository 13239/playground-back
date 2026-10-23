package com.project.playground.domain.authorization.dto.request;

import com.project.playground.domain.authorization.enums.PermissionCode;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.util.Set;

/** 코드와 허용 기능만 입력받으며 기본 역할/슈퍼 관리자 여부는 클라이언트가 지정하지 못한다. */
@Getter
@Builder
@Jacksonized
public class CreateRoleRequest {
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private final String code;
    @NotBlank @Size(max = 50)
    private final String name;
    @Size(max = 255)
    private final String description;
    @NotNull
    private final Set<@NotNull PermissionCode> permissions;
    @Size(max = 300)
    private final String reason;
}


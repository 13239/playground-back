package com.project.playground.domain.usermanagement.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

/** 운영 화면은 활성·비활성만 변경하며 탈퇴 복구나 가입대기 상태 생성은 제공하지 않는다. */
@Getter @Builder @Jacksonized
public class AdminUserStatusRequest {
    @NotNull @Pattern(regexp = "ACTIVE|INACTIVE") private final String status;
    @NotNull @PositiveOrZero private final Long expectedVersion;
    @NotBlank @Size(max = 300) private final String reason;
}

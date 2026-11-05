package com.project.playground.domain.usermanagement.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

/** 운영 화면은 탈퇴 회원을 포함해 활성·비활성·탈퇴 상태를 변경하며 가입대기 상태 생성은 제공하지 않는다. */
@Getter @Builder @Jacksonized
public class AdminUserStatusRequest {
    @NotNull @Pattern(regexp = "ACTIVE|INACTIVE|WITHDRAWN") private final String status;
    @NotNull @PositiveOrZero private final Long expectedVersion;
    @NotBlank @Size(max = 300) private final String reason;
}

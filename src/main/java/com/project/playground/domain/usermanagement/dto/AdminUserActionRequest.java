package com.project.playground.domain.usermanagement.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

/** 세션 종료는 대상의 현재 버전 및 감사에 남길 사유를 필수로 받는다. 개인정보 조회에는 사용하지 않는다. */
@Getter @Builder @Jacksonized
public class AdminUserActionRequest {
    @NotNull @PositiveOrZero private final Long expectedVersion;
    @NotBlank @Size(max = 300) private final String reason;
}

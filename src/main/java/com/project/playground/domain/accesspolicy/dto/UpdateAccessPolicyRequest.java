package com.project.playground.domain.accesspolicy.dto;

import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 제한 스위치·문구 편집에는 허용 역할 필드를 두지 않아 위임받은 관리자의 권한 확대를 차단한다. */
@Getter @Builder @Jacksonized
public class UpdateAccessPolicyRequest {
    @NotNull private final Boolean enabled;
    @NotBlank @Size(max = 300)
    @Pattern(regexp = "[^\\p{Cc}\\p{Cf}]*", message = "안내 문구에는 제어 문자를 사용할 수 없습니다.")
    private final String message;
    @NotNull @PositiveOrZero private final Long expectedVersion;
    @Size(max = 300) private final String reason;
}

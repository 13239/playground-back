package com.project.playground.domain.gomoku.dto.request;

import com.project.playground.domain.gomoku.enums.ForbiddenRule;
import com.project.playground.domain.gomoku.enums.RoomVisibility;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 화면에서 선택 가능한 설정만 수신하며 방장·초대 코드·고정 게임 규칙은 서버가 결정한다. */
@Getter
@Builder
@Jacksonized
public class CreateRoomRequest {
    @NotBlank
    @Pattern(regexp = "[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}")
    private final String requestId;

    @NotBlank(message = "방 이름을 입력해 주세요.")
    @Size(max = 30, message = "방 이름은 30자 이내로 입력해 주세요.")
    @Pattern(regexp = "[^\\p{Cc}\\p{Cf}]+", message = "방 이름에는 제어 문자를 사용할 수 없습니다.")
    private final String title;

    @NotNull
    private final RoomVisibility visibility;

    @Size(max = 8)
    private final String password;

    @NotNull
    @Min(1)
    @Max(5)
    private final Short undoLimit;

    @NotNull
    private final ForbiddenRule forbiddenRule;

    @AssertTrue(message = "비공개 방 비밀번호는 영문 또는 숫자 4~8자로 입력해 주세요.")
    public boolean isPasswordValid() {
        return visibility == RoomVisibility.PRIVATE
                ? password != null && password.matches("[A-Za-z0-9]{4,8}")
                : password == null || password.isEmpty();
    }

    @AssertTrue(message = "무르기 횟수는 1회, 3회, 5회 중에서 선택해 주세요.")
    public boolean isUndoLimitValid() {
        return undoLimit == null || undoLimit == 1 || undoLimit == 3 || undoLimit == 5;
    }
}

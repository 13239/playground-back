package com.project.playground.domain.gomoku.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** 방 번호 또는 초대 코드 중 하나만 받으며 비공개 방은 코드가 있어도 비밀번호를 검증한다. */
@Getter @Builder @Jacksonized
public class JoinRoomRequest {
    @Positive private final Long roomId;
    // 생성 코드의 혼동 문자 제외 규칙을 입력 제한으로 쓰지 않고 BFF와 동일한 영문·숫자 6자리를 검증한다.
    @Pattern(regexp = "[A-Z0-9]{6}") private final String inviteCode;
    @Size(max = 8) private final String password;

    @AssertTrue(message = "방 번호 또는 초대 코드 중 하나를 지정해 주세요.")
    public boolean isDestinationValid() { return (roomId == null) != (inviteCode == null); }
}

package com.project.playground.domain.user.dto.request;

import com.project.playground.domain.user.enums.Gender;
import com.project.playground.global.validation.StringValid;
import com.project.playground.global.validation.StringValidType;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;

// 닉네임·아이디·비밀번호는 필수이며 나머지 개인정보는 DB의 nullable 계약에 맞춰 선택 입력으로 받는다.
@Getter
@Builder
@Jacksonized
public class SignUpRequest {

    @StringValid(label = "닉네임", required = true, max = 50)
    private final String nickname;

    @StringValid(label = "아이디", required = true, min = 4, max = 50)
    private final String username;

    @StringValid(label = "비밀번호", required = true, min = 6, max = 72)
    private final String password;

    @StringValid(label = "이메일", type = StringValidType.EMAIL, max = 100)
    private final String email;

    @StringValid(label = "연락처", type = StringValidType.PHONE, max = 30)
    private final String phone;

    @PastOrPresent(message = "생년월일은 미래 날짜일 수 없습니다.")
    private final LocalDate birthday;

    private final Gender gender;
}

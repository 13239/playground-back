package com.project.playground.domain.user.dto.request;

import com.project.playground.domain.user.enums.Gender;
import com.project.playground.global.validation.StringValid;
import com.project.playground.global.validation.StringValidType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class SignUpRequest {

    @StringValid(label = "이름", required = true, max = 50)
    private String name;

    @StringValid(label = "아이디", required = true, min = 4, max = 50)
    private String username;

    @StringValid(label = "비밀번호", required = true, min = 6, max = 72)
    private String password;

    @StringValid(label = "이메일", type = StringValidType.EMAIL, required = true, max = 100)
    private String email;

    @StringValid(label = "연락처", type = StringValidType.PHONE, required = true, max = 30)
    private String phone;

    @NotNull(message = "생년월일은 필수입니다.")
    @PastOrPresent(message = "생년월일은 미래 날짜일 수 없습니다.")
    private LocalDate birthday;

    @NotNull(message = "성별은 필수입니다.")
    private Gender gender;
}

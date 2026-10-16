package com.project.playground.auth.dto.request;

import com.project.playground.global.validation.StringValid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @StringValid(label = "아이디", required = true, max = 50)
    private String username;

    @StringValid(label = "비밀번호", required = true, max = 72)
    private String password;

    @NotNull(message = "로그인 유지 여부는 필수입니다.")
    private Boolean keepLoggedIn;
}

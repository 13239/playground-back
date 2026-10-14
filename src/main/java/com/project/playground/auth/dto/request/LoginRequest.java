package com.project.playground.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LoginRequest {

    @NotBlank(message = "아이디는 필수입니다.")
    private String username;

    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;

    @NotNull(message = "로그인 유지 여부는 필수입니다.")
    private Boolean keepLoggedIn;
}

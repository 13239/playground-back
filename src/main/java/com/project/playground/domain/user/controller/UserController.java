package com.project.playground.domain.user.controller;

import com.project.playground.domain.user.dto.request.SignUpRequest;
import com.project.playground.domain.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/sign-up")
    @ResponseStatus(HttpStatus.CREATED)
    public void signUp(@Valid @RequestBody SignUpRequest request) {
        userService.signUp(request);
    }
}

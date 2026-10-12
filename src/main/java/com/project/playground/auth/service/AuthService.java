package com.project.playground.auth.service;

import com.project.playground.auth.dto.request.LoginRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;

    private Authentication authenticate(LoginRequest request) {
        return authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(request.getUsername(), request.getPassword()));
    }
}

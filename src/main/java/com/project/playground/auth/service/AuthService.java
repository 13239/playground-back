package com.project.playground.auth.service;

import com.project.playground.auth.dto.request.LoginRequest;
import com.project.playground.auth.dto.request.RefreshRequest;
import com.project.playground.auth.dto.response.LoginResponse;
import com.project.playground.auth.repository.RefreshTokenRepository;
import com.project.playground.domain.user.entity.User;
import com.project.playground.domain.user.repository.reader.UserReader;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import com.project.playground.global.security.CustomUserDetails;
import com.project.playground.global.security.jwt.JwtProperties;
import com.project.playground.global.security.jwt.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;

    private final JwtTokenService jwtTokenService;

    private final RefreshTokenRepository refreshTokenRepository;

    private final JwtProperties jwtProperties;

    private final UserReader userReader;

    public LoginResponse login(LoginRequest request) {
        Authentication authentication;

        try {
            authentication = authenticate(request);
        } catch (BadCredentialsException | AccountStatusException e) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();

        Jwt accessToken = jwtTokenService.createAccessToken(user.getId());
        String refreshToken = jwtTokenService.createRefreshToken();

        refreshTokenRepository.save(user.getId(), refreshToken, jwtProperties.refreshTokenTtl());

        long expiresIn = Duration.between(accessToken.getIssuedAt(), accessToken.getExpiresAt()).toSeconds();

        return LoginResponse.builder()
                .accessToken(accessToken.getTokenValue())
                .refreshToken(refreshToken)
                .tokenType(OAuth2AccessToken.TokenType.BEARER.getValue())
                .expiresIn(expiresIn)
                .build();
    }

    public LoginResponse refresh(RefreshRequest request) {
        Long id = refreshTokenRepository.consume(request.getRefreshToken()).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        User user = userReader.findActiveById(id).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        Jwt accessToken = jwtTokenService.createAccessToken(user.getId());
        String refreshToken = jwtTokenService.createRefreshToken();

        refreshTokenRepository.save(user.getId(), refreshToken, jwtProperties.refreshTokenTtl());

        long expiresIn = Duration.between(accessToken.getIssuedAt(), accessToken.getExpiresAt()).toSeconds();

        return LoginResponse.builder()
                .accessToken(accessToken.getTokenValue())
                .refreshToken(refreshToken)
                .tokenType(OAuth2AccessToken.TokenType.BEARER.getValue())
                .expiresIn(expiresIn)
                .build();
    }

    private Authentication authenticate(LoginRequest request) {
        return authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(request.getUsername(), request.getPassword()));
    }
}

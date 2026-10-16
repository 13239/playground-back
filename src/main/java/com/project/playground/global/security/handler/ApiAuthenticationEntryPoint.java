package com.project.playground.global.security.handler;

import com.project.playground.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증 정보 누락 및 Bearer 인증 실패를 공통 오류 응답으로 변환한다.
 */
@Component
@RequiredArgsConstructor
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityProblemWriter problemWriter;

    // RFC 6750에 따른 Bearer 인증 상태와 WWW-Authenticate 헤더 생성을 위임한다.
    private final BearerTokenAuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        delegate.commence(request, response, authException);

        ErrorCode errorCode = ErrorCode.AUTHENTICATION_REQUIRED;

        if (authException instanceof OAuth2AuthenticationException oauth2Exception) {
            // 요청 형식 오류, 권한 부족, 토큰 유효성 오류를 구분한다.
            errorCode = switch (oauth2Exception.getError().getErrorCode()) {
                case OAuth2ErrorCodes.INVALID_REQUEST -> ErrorCode.INVALID_AUTHENTICATION_REQUEST;
                case OAuth2ErrorCodes.INSUFFICIENT_SCOPE -> ErrorCode.ACCESS_DENIED;
                default -> ErrorCode.INVALID_ACCESS_TOKEN;
            };
        }

        problemWriter.write(request, response, errorCode);
    }
}

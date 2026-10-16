package com.project.playground.global.security.handler;

import com.project.playground.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증된 사용자의 접근 권한 부족을 공통 403 오류 응답으로 변환한다.
 */
@Component
@RequiredArgsConstructor
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityProblemWriter problemWriter;

    // Bearer 인증에 필요한 접근 거부 응답 헤더를 기본 처리기에 위임한다.
    private final BearerTokenAccessDeniedHandler delegate = new BearerTokenAccessDeniedHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        delegate.handle(request, response, accessDeniedException);
        problemWriter.write(request, response, ErrorCode.ACCESS_DENIED);
    }
}

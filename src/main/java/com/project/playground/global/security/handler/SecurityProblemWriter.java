package com.project.playground.global.security.handler;

import com.project.playground.global.exception.ApiProblemFactory;
import com.project.playground.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * 보안 필터에서 발생한 오류를 공통 ProblemDetail JSON 응답으로 작성한다.
 */
@Component
@RequiredArgsConstructor
public class SecurityProblemWriter {

    private final JsonMapper jsonMapper;

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode) throws IOException {
        write(request, response, errorCode, errorCode.getMessage());
    }

    /** 관리자 검증을 통과한 접속 제한 문구에만 동적 상세 메시지를 허용한다. */
    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode, String notice) throws IOException {
        // 이미 전송된 응답은 다시 작성하지 않는다.
        if (response.isCommitted()) {
            return;
        }

        ProblemDetail problem = ApiProblemFactory.create(errorCode);
        if (errorCode == ErrorCode.ACCESS_RESTRICTED && com.project.playground.auth.audit.LoginAuditContext.current() == null) problem.setDetail(notice);

        // MVC를 거치지 않으므로 요청 경로를 직접 설정하며 쿼리 문자열은 포함하지 않는다.
        problem.setInstance(URI.create(request.getRequestURI()));

        // 직렬화를 먼저 완료한 후 UTF-8 JSON 응답을 작성한다.
        byte[] body = jsonMapper.writeValueAsBytes(problem);

        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
        // 증명 저장 상한은 이력을 조기 삭제하지 않고 요청을 제한한다. 브라우저에 짧은 재시도 대기를 안내한다.
        if (errorCode == ErrorCode.BROWSER_PROOF_RATE_LIMIT_EXCEEDED) response.setHeader(HttpHeaders.RETRY_AFTER, "60");

        // 기본 Bearer 처리기가 설정한 WWW-Authenticate 헤더를 유지한다.
        response.getOutputStream().write(body);
    }
}

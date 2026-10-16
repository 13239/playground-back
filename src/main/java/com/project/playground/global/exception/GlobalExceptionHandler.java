package com.project.playground.global.exception;

import com.project.playground.auth.ratelimit.LoginRateLimitException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // DTO 필드 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail methodArgumentNotValidException(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();

        for (FieldError error : e.getFieldErrors()) {
            String message = Objects.requireNonNullElse(error.getDefaultMessage(), "유효하지 않은 값입니다.");

            errors.merge(error.getField(), message, (a, b) -> a + "\n" + b);
        }

        ProblemDetail problem = ApiProblemFactory.create(ErrorCode.VALIDATION_FAILED);
        problem.setProperty("errors", errors);

        return problem;
    }

    // JSON 형식 오류 또는 역직렬화 실패
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMessageNotReadable(HttpMessageNotReadableException e) {
        return ApiProblemFactory.create(ErrorCode.INVALID_REQUEST_BODY);
    }

    // 비즈니스 규칙 위반
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail businessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();

        return ApiProblemFactory.create(errorCode);
    }

    // 로그인 시도 제한 초과
    @ExceptionHandler(LoginRateLimitException.class)
    public ResponseEntity<ProblemDetail> loginRateLimitException(LoginRateLimitException e) {
        long retryAfterSeconds = e.getRetryAfterSeconds();

        ProblemDetail problem = ApiProblemFactory.create(e.getErrorCode());

        // 프론트가 응답 본문에서도 재시도 대기 시간을 확인할 수 있도록 제공한다.
        problem.setProperty("retryAfterSeconds", retryAfterSeconds);

        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
                .body(problem);
    }
}

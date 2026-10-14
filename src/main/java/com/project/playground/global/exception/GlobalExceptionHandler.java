package com.project.playground.global.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다.");

        problem.setProperty("errors", errors);

        return problem;
    }

    // JSON 형식 오류 또는 역직렬화 실패
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMessageNotReadable(HttpMessageNotReadableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "요청 본문의 형식 또는 값이 올바르지 않습니다.");
    }

    // 비즈니스 규칙 위반
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail businessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();

        return ProblemDetail.forStatusAndDetail(errorCode.getStatus(), errorCode.getMessage());
    }
}

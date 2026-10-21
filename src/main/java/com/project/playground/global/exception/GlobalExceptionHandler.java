package com.project.playground.global.exception;

import com.project.playground.auth.chat.ChatTicketRateLimitException;
import com.project.playground.auth.ratelimit.LoginRateLimitException;
import com.project.playground.auth.ratelimit.SignUpRateLimitException;
import com.project.playground.domain.gomoku.service.RoomRateLimitException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 기존 로그인 제한과 같은 Retry-After 계약으로 채팅 접속 재시도 간격을 전달한다.
    @ExceptionHandler(ChatTicketRateLimitException.class)
    public ResponseEntity<ProblemDetail> chatTicketRateLimitException(ChatTicketRateLimitException exception) {
        return rateLimitResponse(exception.getErrorCode(), exception.getRetryAfterSeconds());
    }

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

    // 인증 서비스 오류와 DB·Redis 연결 실패·시간 초과는 인증 무효와 구분한다.
    @ExceptionHandler({AuthenticationServiceException.class, DataAccessResourceFailureException.class, TransientDataAccessException.class})
    public ResponseEntity<ProblemDetail> authenticationServiceUnavailable(Exception e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .cacheControl(CacheControl.noStore())
                .body(ApiProblemFactory.create(ErrorCode.AUTH_SERVICE_UNAVAILABLE));
    }

    // 로그인 시도 제한 초과
    @ExceptionHandler(LoginRateLimitException.class)
    public ResponseEntity<ProblemDetail> loginRateLimitException(LoginRateLimitException e) {
        return rateLimitResponse(e.getErrorCode(), e.getRetryAfterSeconds());
    }

    // 가입 제한도 동일한 Retry-After 계약을 사용하되 로그인 실패 상태를 변경하지 않는다.
    @ExceptionHandler(SignUpRateLimitException.class)
    public ResponseEntity<ProblemDetail> signUpRateLimitException(SignUpRateLimitException e) {
        return rateLimitResponse(e.getErrorCode(), e.getRetryAfterSeconds());
    }

    // 방 API는 별도 계정 예산을 사용하면서 동일한 Retry-After 응답 계약을 따른다.
    @ExceptionHandler(RoomRateLimitException.class)
    public ResponseEntity<ProblemDetail> roomRateLimitException(RoomRateLimitException exception) {
        return rateLimitResponse(exception.getErrorCode(), exception.getRetryAfterSeconds());
    }

    private ResponseEntity<ProblemDetail> rateLimitResponse(ErrorCode errorCode, long retryAfterSeconds) {
        ProblemDetail problem = ApiProblemFactory.create(errorCode);

        // 프론트가 응답 본문에서도 재시도 대기 시간을 확인할 수 있도록 제공한다.
        problem.setProperty("retryAfterSeconds", retryAfterSeconds);

        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
                .body(problem);
    }
}

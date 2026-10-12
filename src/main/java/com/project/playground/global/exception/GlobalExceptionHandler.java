package com.project.playground.global.exception;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> methodArgumentNotValidException(MethodArgumentNotValidException e) {
        Map<String, String> errors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> Objects.requireNonNullElse(error.getDefaultMessage(), error.getCode()),
                        (o, n) -> String.join(", ", o, n)
                ));

        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> businessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.getStatus(), errorCode.getMessage());

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(problem);
    }
}

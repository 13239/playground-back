package com.project.playground.domain.adminmonitor.controller;

import com.project.playground.global.exception.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;

/** 잘못된 기간 코드를 400으로 명시하고 서버 내부 입력 예외를 공개하지 않는다. */
@Order(Ordered.HIGHEST_PRECEDENCE) @RestControllerAdvice(assignableTypes = AdminMonitoringController.class)
public class AdminMonitoringExceptionHandler {
    @ExceptionHandler({HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail invalid(Exception ignored) { return ApiProblemFactory.create(ErrorCode.VALIDATION_FAILED); }
}

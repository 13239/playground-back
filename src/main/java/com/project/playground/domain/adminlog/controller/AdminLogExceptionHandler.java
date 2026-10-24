package com.project.playground.domain.adminlog.controller;

import com.project.playground.global.exception.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;

/** 잘못된 날짜·필터는 400, 저장소 오류는 503으로 구분하고 SQL/내부 메시지는 숨긴다. */
@Order(Ordered.HIGHEST_PRECEDENCE) @RestControllerAdvice(assignableTypes = AdminLogController.class)
public class AdminLogExceptionHandler {
    @ExceptionHandler({HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ProblemDetail invalid(Exception ignored) { return ApiProblemFactory.create(ErrorCode.VALIDATION_FAILED); }
    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail unavailable(Exception ignored) { return ApiProblemFactory.create(ErrorCode.ADMIN_LOG_UNAVAILABLE); }
}

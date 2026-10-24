package com.project.playground.domain.usermanagement.controller;

import com.project.playground.global.exception.*;
import org.springframework.core.*;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;
import org.springframework.web.bind.MissingServletRequestParameterException;

/** 조회 조건·버전·저장소 오류를 구분하고 내부 SQL/개인정보를 응답하지 않는다. */
@Order(Ordered.HIGHEST_PRECEDENCE) @RestControllerAdvice(assignableTypes = AdminUserController.class)
public class AdminUserExceptionHandler {
    @ExceptionHandler({HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ProblemDetail invalid(Exception e) { return ApiProblemFactory.create(ErrorCode.VALIDATION_FAILED); }
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail conflict(Exception e) { return ApiProblemFactory.create(ErrorCode.USER_MANAGEMENT_CHANGED); }
    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail unavailable(Exception e) { return ApiProblemFactory.create(ErrorCode.USER_MANAGEMENT_UNAVAILABLE); }
}

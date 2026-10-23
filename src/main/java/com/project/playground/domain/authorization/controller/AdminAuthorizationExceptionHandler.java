package com.project.playground.domain.authorization.controller;

import com.project.playground.global.exception.ApiProblemFactory;
import com.project.playground.domain.accesspolicy.controller.AdminAccessPolicyController;
import com.project.playground.global.exception.ErrorCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/** 권한 편집 전용 오류 처리를 분리해 기존 게임/인증 API의 오류 계약을 바꾸지 않는다. */
@Order(Ordered.HIGHEST_PRECEDENCE)
// 접속 정책 편집도 같은 파라미터·버전·저장소 오류 계약을 따른다.
@RestControllerAdvice(assignableTypes = {AdminAuthorizationController.class,
        AdminAccessPolicyController.class})
public class AdminAuthorizationExceptionHandler {
    @ExceptionHandler({HandlerMethodValidationException.class, MissingServletRequestParameterException.class})
    public ProblemDetail invalidParameter(Exception exception) {
        return ApiProblemFactory.create(ErrorCode.VALIDATION_FAILED);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail versionConflict(ObjectOptimisticLockingFailureException exception) {
        return ApiProblemFactory.create(ErrorCode.AUTHORIZATION_CHANGED);
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, TransientDataAccessException.class})
    public ProblemDetail unavailable(Exception exception) {
        return ApiProblemFactory.create(ErrorCode.AUTHORIZATION_SERVICE_UNAVAILABLE);
    }
}

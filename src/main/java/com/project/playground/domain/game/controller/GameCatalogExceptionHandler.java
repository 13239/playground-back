package com.project.playground.domain.game.controller;

import com.project.playground.global.exception.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 카탈로그 조회 장애·편집 충돌을 로그인 만료와 구분한다. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {GameCatalogController.class, AdminGameController.class})
public class GameCatalogExceptionHandler {
    @ExceptionHandler({HandlerMethodValidationException.class, MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail invalid(Exception exception) { return ApiProblemFactory.create(ErrorCode.VALIDATION_FAILED); }
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail changed(Exception exception) { return ApiProblemFactory.create(ErrorCode.GAME_CATALOG_CHANGED); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflict(Exception exception) { return ApiProblemFactory.create(ErrorCode.GAME_CATALOG_CONFLICT); }
    @ExceptionHandler({DataAccessResourceFailureException.class, TransientDataAccessException.class})
    public ResponseEntity<ProblemDetail> unavailable(Exception exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore()).body(ApiProblemFactory.create(ErrorCode.GAME_CATALOG_UNAVAILABLE));
    }
}

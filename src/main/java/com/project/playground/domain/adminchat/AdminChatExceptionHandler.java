package com.project.playground.domain.adminchat;

import com.project.playground.global.exception.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;

/** 입력 오류와 저장소 장애를 구분하고 내부 주소·증명·드라이버 메시지는 응답에 포함하지 않는다. */
@Order(Ordered.HIGHEST_PRECEDENCE) @RestControllerAdvice(assignableTypes = AdminChatController.class)
public class AdminChatExceptionHandler {
    @ExceptionHandler({HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail invalid(Exception ignored) { return ApiProblemFactory.create(ErrorCode.VALIDATION_FAILED); }
    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail unavailable(Exception ignored) { return ApiProblemFactory.create(ErrorCode.ADMIN_CHAT_UNAVAILABLE); }
}

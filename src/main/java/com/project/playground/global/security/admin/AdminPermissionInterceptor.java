package com.project.playground.global.security.admin;

import com.project.playground.domain.authorization.dto.response.AdminContextResponse;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/** 필터의 관리자 진입 검사에 더해 실제 MVC 핸들러가 요구하는 기능을 확인한다. */
public class AdminPermissionInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        RequireAdminPermission required = null;
        if (handler instanceof HandlerMethod method) {
            required = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequireAdminPermission.class);
            if (required == null) required = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequireAdminPermission.class);
        }
        // 다른 경로에 관리자 애너테이션을 잘못 붙여도 요청 권한 정보가 없으면 허용하지 않는다.
        if (required == null && !AdminAccessFilter.isAdminPath(request)) return true;
        if (!(request.getAttribute(AdminContextResponse.ATTRIBUTE) instanceof AdminContextResponse context)
                || required == null || required.value().length == 0
                || !Arrays.stream(required.value()).allMatch(context::allows)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return true;
    }
}


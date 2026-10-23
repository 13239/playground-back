package com.project.playground.global.security.admin;

import com.project.playground.domain.authorization.dto.response.AdminContextResponse;
import com.project.playground.domain.authorization.enums.PermissionCode;
import com.project.playground.domain.authorization.service.AdminAuthorizationService;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import com.project.playground.global.security.handler.SecurityProblemWriter;
import com.project.playground.global.security.session.Session;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 인증·브라우저 증명 뒤, MVC 매핑보다 먼저 모든 관리자 경로의 DB 접근 권한을 검사한다. */
@RequiredArgsConstructor
public class AdminAccessFilter extends OncePerRequestFilter {
    private final AdminAuthorizationService authorization;
    private final SecurityProblemWriter errors;

    public static boolean isAdminPath(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/api/admin") || path.startsWith("/api/admin/");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isAdminPath(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Session session)) {
            errors.write(request, response, ErrorCode.AUTHENTICATION_REQUIRED);
            return;
        }
        try {
            // 권한 스냅샷은 현재 요청에서만 재사용하고 다른 요청/세션에 캐시하지 않는다.
            request.setAttribute(AdminContextResponse.ATTRIBUTE,
                    authorization.require(session.getId(), PermissionCode.ADMIN_ACCESS));
        } catch (BusinessException exception) {
            errors.write(request, response, exception.getErrorCode());
            return;
        } catch (DataAccessException exception) {
            errors.write(request, response, ErrorCode.AUTHORIZATION_SERVICE_UNAVAILABLE);
            return;
        }
        chain.doFilter(request, response);
    }
}


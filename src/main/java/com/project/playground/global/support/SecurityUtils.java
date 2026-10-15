package com.project.playground.global.support;

import com.project.playground.global.security.session.Session;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Session getSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof Session session) {
            return session;
        }

        throw new AuthenticationCredentialsNotFoundException("인증된 사용자 세션이 없습니다.");
    }
}

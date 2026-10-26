package com.project.playground.auth.proof;

import com.project.playground.auth.clientip.LoginClientIpResolver;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Bearer 인증 후 브라우저 키도 검증한다. 원격 HTTP의 명시적 dev 예외는 설정 검증 후에만 허용한다. */
@RequiredArgsConstructor
public class BrowserProofFilter extends OncePerRequestFilter {

    private final BrowserProofService proofs;
    private final LoginClientIpResolver clientIps;
    private final SecurityProblemWriter errors;
    private final boolean proofRequired;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 비개인화된 게임·장르·필터 카탈로그 GET만 브라우저 개인키 없이 읽는다.
        // 관리용 /api/admin/game-filters와 게임 실행 요청은 증명 예외에 포함하지 않는다.
        if (request.getMethod().equals("GET") && (request.getRequestURI().equals("/api/games")
                || request.getRequestURI().matches("/api/games/[A-Z][A-Z0-9_]{0,63}")
                || request.getRequestURI().equals("/api/game-genres")
                || request.getRequestURI().equals("/api/game-filters"))) return true;
        return request.getMethod().equals("POST") && (request.getRequestURI().equals("/api/auth/proof-challenge")
                || request.getRequestURI().equals("/api/user/sign-up"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        // dev에서도 BFF 서버 인증과 보호 API의 Bearer·Redis 검증은 그대로 수행한다.
        if (!proofRequired) {
            request.setAttribute(BrowserProof.ATTRIBUTE, BrowserProof.forDevelopmentHttp());
            chain.doFilter(request, response);
            return;
        }
        try {
            boolean login = request.getRequestURI().equals("/api/auth/login") && request.getMethod().equals("POST");
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            Session session = authentication != null && authentication.getPrincipal() instanceof Session value ? value : null;
            BrowserProof proof = proofs.verify(request, login ? "login" : session == null ? null : session.getSessionId().toString(),
                    session == null ? null : session.getBrowserKeyThumbprint(), login ? clientIps.resolve(request) : "");
            request.setAttribute(BrowserProof.ATTRIBUTE, proof);
        } catch (BusinessException e) {
            errors.write(request, response, e.getErrorCode());
            return;
        } catch (DataAccessException e) {
            errors.write(request, response, ErrorCode.AUTH_SERVICE_UNAVAILABLE);
            return;
        }
        chain.doFilter(request, response);
    }
}

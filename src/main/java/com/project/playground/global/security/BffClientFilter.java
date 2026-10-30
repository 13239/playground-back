package com.project.playground.global.security;

import com.project.playground.global.exception.ErrorCode;
import com.project.playground.global.security.handler.SecurityProblemWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.Base64;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/** prod는 Spring TLS가 검증한 인증서만 신뢰한다. dev의 내부 HTTP 예외는 전용 공유 키로 BFF를 확인한다. */
public class BffClientFilter extends OncePerRequestFilter {

    public static final String DEV_HEADER = "X-BFF-Dev-Secret";

    private final boolean required;
    private final Set<String> fingerprints;
    private final SecurityProblemWriter errors;

    private final byte[] devSecret;

    public BffClientFilter(boolean required, String fingerprints, String devSharedSecret, Environment environment, SecurityProblemWriter errors) {
        this.required = required;
        this.errors = errors;
        this.fingerprints = fingerprints.isBlank() ? Set.of() : java.util.Arrays.stream(fingerprints.split(","))
                .map(String::trim).map(String::toLowerCase).collect(Collectors.toUnmodifiableSet());
        // local/dev/prod를 동시에 활성화해 다른 환경의 보안 예외를 섞지 않는다.
        long profileCount = java.util.Arrays.stream(environment.getActiveProfiles()).filter(Set.of("local", "dev", "prod")::contains).count();
        if (profileCount != 1 && !(profileCount == 0 && environment.acceptsProfiles(Profiles.of("test")))) {
            throw new IllegalArgumentException("local, dev, prod 중 하나의 프로필만 선택해야 합니다.");
        }
        boolean devHttp = !required && environment.acceptsProfiles(Profiles.of("dev"));
        // 명시적인 local/test 또는 공유 키를 요구하는 dev 외에서는 mTLS를 끌 수 없다. prod는 인증서 지문이 필수다.
        if (!required && environment.acceptsProfiles(Profiles.of("prod"))
                || !required && !devHttp && !environment.acceptsProfiles(Profiles.of("local", "test"))
                || required && (this.fingerprints.isEmpty() || this.fingerprints.stream().anyMatch(value -> !value.matches("[a-f0-9]{64}")))) {
            throw new IllegalArgumentException("BFF mTLS 인증 설정이 올바르지 않습니다.");
        }
        try {
            // dev 키는 JWT·쿠키 키와 분리한 무작위 32바이트의 정규 Base64 문자열만 허용한다.
            if (devHttp && (Base64.getDecoder().decode(devSharedSecret).length != 32
                    || !Base64.getEncoder().encodeToString(Base64.getDecoder().decode(devSharedSecret)).equals(devSharedSecret))) {
                throw new IllegalArgumentException();
            }
            this.devSecret = devHttp ? devSharedSecret.getBytes(StandardCharsets.US_ASCII) : null;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("dev HTTP에는 별도 32바이트 Base64 BFF 인증 키가 필요합니다.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        com.project.playground.auth.audit.LoginAuditContext.step("BFF_AUTHENTICATION");
        boolean accepted = false;
        try {
            if (required) {
                com.project.playground.auth.audit.LoginAuditContext.reason("BFF_CERTIFICATE_MISSING");
                Object certificates = request.getAttribute("jakarta.servlet.request.X509Certificate");
                if (request.isSecure() && certificates instanceof X509Certificate[] values && values.length > 0) {
                    com.project.playground.auth.audit.LoginAuditContext.reason("BFF_CERTIFICATE_VALIDATION_FAILED");
                    values[0].checkValidity();
                    String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(values[0].getEncoded()));
                    accepted = fingerprints.contains(fingerprint);
                    if (!accepted) com.project.playground.auth.audit.LoginAuditContext.reason("BFF_CERTIFICATE_REJECTED");
                }
            } else if (devSecret != null) {
                com.project.playground.auth.audit.LoginAuditContext.reason("BFF_DEV_SECRET_MISMATCH");
                // Docker IP를 인증 수단으로 삼지 않는다. 지정 헤더 한 건만 상수 시간 비교하며 키를 로그에 남기지 않는다.
                String provided = request.getHeader(DEV_HEADER);
                accepted = provided != null && provided.length() == devSecret.length
                        && Collections.list(request.getHeaders(DEV_HEADER)).size() == 1
                        && MessageDigest.isEqual(devSecret, provided.getBytes(StandardCharsets.US_ASCII));
            } else {
                com.project.playground.auth.audit.LoginAuditContext.reason("BFF_LOOPBACK_REQUIRED");
                // 개발 예외도 실제 루프백 연결에만 허용하며 전달 헤더나 Host 값을 사용하지 않는다.
                String remote = request.getRemoteAddr();
                accepted = (remote.matches("[0-9.]+") || remote.contains(":")) && InetAddress.getByName(remote).isLoopbackAddress();
            }
        } catch (Exception e) { com.project.playground.auth.audit.LoginAuditContext.exception(e); /* 원문은 기록하지 않는다. */ }
        if (!accepted) { errors.write(request, response, ErrorCode.BFF_AUTHENTICATION_REQUIRED); return; }
        com.project.playground.auth.audit.LoginAuditContext.fact("bffVerified", true);
        chain.doFilter(request, response);
    }
}

package com.project.playground.domain.shooting.security;

import com.project.playground.domain.shooting.config.ShootingProperties;
import com.project.playground.global.exception.ErrorCode;
import com.project.playground.global.security.handler.SecurityProblemWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 메서드·경로·본문·시각·일회용 nonce를 함께 서명하여 브라우저의 기록 제출과 요청 재생을 차단한다. */
public final class ShootingServiceAuthFilter extends OncePerRequestFilter {
    private static final int MAX_BODY = 16_384;
    private final ShootingProperties properties;
    private final StringRedisTemplate redis;
    private final SecurityProblemWriter errors;

    public ShootingServiceAuthFilter(
            ShootingProperties properties,
            StringRedisTemplate redis,
            SecurityProblemWriter errors) {
        this.properties = properties;
        this.redis = redis;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!properties.enabled()) {
            errors.write(request, response, ErrorCode.SHOOTING_UNAVAILABLE);
            return;
        }
        byte[] body;
        try {
            if (!request.getMethod().equals("POST")
                    || request.getHeader("Origin") != null
                    || request.getQueryString() != null
                    || request.getContentLengthLong() > MAX_BODY)
                throw new IllegalArgumentException();
            body = request.getInputStream().readNBytes(MAX_BODY + 1);
            if (body.length > MAX_BODY) throw new IllegalArgumentException();
            String timestamp = single(request, "X-Game-Timestamp");
            String nonce = single(request, "X-Game-Nonce");
            String signature = single(request, "X-Game-Signature");
            if (!timestamp.matches("[0-9]{13}")
                    || !nonce.matches("[0-9a-f-]{36}")
                    || Math.abs(System.currentTimeMillis() - Long.parseLong(timestamp)) > 30_000)
                throw new IllegalArgumentException();
            String hash =
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
            String canonical =
                    timestamp + "\n" + nonce + "\nPOST\n" + request.getRequestURI() + "\n" + hash;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(
                    new SecretKeySpec(
                            Base64.getDecoder().decode(properties.serviceSecret()), "HmacSHA256"));
            byte[] expected = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            byte[] actual = Base64.getUrlDecoder().decode(signature);
            if (!MessageDigest.isEqual(expected, actual)) throw new IllegalArgumentException();
            if (!Boolean.TRUE.equals(
                    redis.opsForValue()
                            .setIfAbsent(
                                    "shooting:service:nonce:" + nonce,
                                    "1",
                                    Duration.ofSeconds(60)))) throw new IllegalArgumentException();
        } catch (org.springframework.dao.DataAccessException exception) {
            errors.write(request, response, ErrorCode.SHOOTING_UNAVAILABLE);
            return;
        } catch (Exception exception) {
            // 키·원문 본문·서명·저장소 상세를 로그와 응답에 노출하지 않는다.
            errors.write(request, response, ErrorCode.SHOOTING_SERVICE_AUTH_REQUIRED);
            return;
        }
        response.setHeader("Cache-Control", "no-store");
        chain.doFilter(new CachedRequest(request, body), response);
    }

    private static String single(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null
                || value.length() > 128
                || Collections.list(request.getHeaders(name)).size() != 1)
            throw new IllegalArgumentException();
        return value;
    }

    /** 서명 검증에서 읽은 제한 크기 본문을 MVC가 동일한 바이트로 역직렬화한다. */
    private static final class CachedRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            var input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    try {
                        if (!isFinished()) listener.onDataAvailable();
                        if (isFinished()) listener.onAllDataRead();
                    } catch (IOException exception) {
                        listener.onError(exception);
                    }
                }
            };
        }
    }
}

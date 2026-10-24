package com.project.playground.domain.adminchat;

import com.project.playground.auth.chat.ChatTicketProperties;
import com.project.playground.global.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;

/** 관리자 조회와 운영 점검의 고정된 내부 채팅 경로만 호출하고 짧은 일회용 증명을 부여한다. */
@Component
public class AdminChatClient {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_BYTES = 512 * 1024;
    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final RestClient client;
    private final String origin;

    public AdminChatClient(StringRedisTemplate redis, JsonMapper json, ChatTicketProperties chat, Environment environment,
            @Value("${chat.admin.origin:}") String configuredOrigin) {
        this.redis = redis; this.json = json;
        // local은 현재 WebSocket 포트를 재사용한다. 배포 환경은 내부 전용 주소를 명시하며 공개 프록시로 우회하지 않는다.
        String source = configuredOrigin.strip();
        if (source.isEmpty() && environment.matchesProfiles("local")) {
            URI ws = URI.create(chat.getWebsocketUrl());
            source = ("wss".equals(ws.getScheme()) ? "https" : "http") + "://" + ws.getRawAuthority();
        }
        origin = validateOrigin(source, environment.matchesProfiles("prod"));
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = RestClient.builder().requestFactory(factory).build();
    }

    public <T> T get(String path, Class<T> type) {
        if (origin == null) throw unavailable();
        // 운영 점검은 쿼리 없는 health 한 경로만 추가한다. 임의의 내부 URL로 확장하지 않는다.
        if (!path.equals("/internal/admin/chat/health") && !path.matches("/internal/admin/chat/messages(?:\\?[^#\\r\\n]*|/[a-f0-9]{24})?")) throw new IllegalArgumentException("허용되지 않은 내부 경로");
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String key = "chat:admin:capability:" + digest(token);
        try {
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, digest("GET\n" + path), Duration.ofSeconds(10)))) throw unavailable();
            return client.get().uri(URI.create(origin + path)).accept(MediaType.APPLICATION_JSON).header("X-Chat-Admin-Capability", token)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().value() == 404) throw new BusinessException(ErrorCode.ADMIN_CHAT_NOT_FOUND);
                        if (!response.getStatusCode().is2xxSuccessful() || response.getHeaders().getContentType() == null
                                || !MediaType.APPLICATION_JSON.isCompatibleWith(response.getHeaders().getContentType())) throw unavailable();
                        byte[] body = response.getBody().readNBytes(MAX_BYTES + 1);
                        if (body.length > MAX_BYTES) throw unavailable();
                        T value = json.readValue(body, type);
                        if (value == null) throw unavailable();
                        return value;
                    });
        } catch (BusinessException exception) { throw exception; }
        catch (Exception exception) { throw unavailable(); }
        finally {
            // 연결 전에 실패한 증명도 제거한다. 정리 실패 시에도 10초 TTL 이후 자동 만료한다.
            try { redis.delete(key); } catch (Exception ignored) { }
        }
    }
    private String validateOrigin(String value, boolean production) {
        if (value.isEmpty()) return null;
        URI uri = URI.create(value);
        // chat의 prod는 HTTPS로 동작하므로 운영 증명이 평문 경로로 잘못 전송되지 않게 한다.
        if (!Set.of("http", "https").contains(uri.getScheme()) || production && !"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null || !Objects.requireNonNullElse(uri.getRawPath(), "").isEmpty()) {
            throw new IllegalArgumentException("CHAT_ADMIN_ORIGIN은 경로 없는 내부 HTTP(S) 주소여야 하며 prod에서는 HTTPS가 필요합니다.");
        }
        return value;
    }
    private String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private BusinessException unavailable() { return new BusinessException(ErrorCode.ADMIN_CHAT_UNAVAILABLE); }
}

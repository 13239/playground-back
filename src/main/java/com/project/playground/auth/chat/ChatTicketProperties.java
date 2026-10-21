package com.project.playground.auth.chat;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Set;

/** 기존 브라우저 Origin을 재사용하고 접속 증명에 바인딩할 공개 WebSocket 주소를 검증한다. */
@Getter
@Component
public class ChatTicketProperties {

    public static final long TICKET_TTL_MILLIS = 30_000;
    public static final int MAX_PENDING_PER_SESSION = 5;
    public static final int MAX_ISSUES_PER_MINUTE = 30;

    private final String origin;
    private final String websocketUrl;
    private final boolean proofRequired;

    public ChatTicketProperties(
            @Value("${auth.proof.origin}") String origin,
            @Value("${chat.auth.websocket-url:}") String websocketUrl,
            @Value("${auth.proof.required:true}") boolean proofRequired,
            Environment environment) {
        try {
            URI source = URI.create(origin);
            URI target = websocketUrl.isBlank()
                    ? URI.create(origin.replaceFirst("^http", "ws") + "/ws/chat")
                    : URI.create(websocketUrl);
            boolean local = environment.matchesProfiles("local");
            boolean prod = environment.matchesProfiles("prod");
            if (!("http".equals(source.getScheme()) || "https".equals(source.getScheme())) || source.getHost() == null
                    || source.getRawUserInfo() != null || source.getRawQuery() != null || source.getRawFragment() != null
                    || source.getRawPath() != null && !source.getRawPath().isEmpty()
                    || !("ws".equals(target.getScheme()) || "wss".equals(target.getScheme())) || target.getHost() == null
                    || target.getRawUserInfo() != null || target.getRawQuery() != null || target.getRawFragment() != null
                    || !"/ws/chat".equals(target.getRawPath())
                    || "https".equals(source.getScheme()) && !"wss".equals(target.getScheme())
                    || prod && (!"https".equals(source.getScheme()) || !"wss".equals(target.getScheme()))
                    || !local && !source.getRawAuthority().equals(target.getRawAuthority())
                    || local && (!Set.of("localhost", "127.0.0.1", "[::1]").contains(source.getHost())
                        || !Set.of("localhost", "127.0.0.1", "[::1]").contains(target.getHost()))
                    || !proofRequired && !environment.matchesProfiles("dev")) {
                throw new IllegalArgumentException();
            }
            this.origin = origin;
            this.websocketUrl = target.toString();
            this.proofRequired = proofRequired;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("채팅 Origin·WebSocket 주소·브라우저 증명 설정이 올바르지 않습니다.");
        }
    }
}

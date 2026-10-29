package com.project.playground.domain.shooting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.Base64;

/** 게임 서버의 인증 키와 접속 주소만 바인딩한다. 비활성 환경의 기존 서비스 기동은 유지한다. */
@ConfigurationProperties("game.shooting")
public record ShootingProperties(
        boolean enabled, String serviceSecret, String publicUrl, String appOrigin) {
    public ShootingProperties {
        serviceSecret = serviceSecret == null ? "" : serviceSecret;
        appOrigin = appOrigin == null || appOrigin.isBlank() ? "http://localhost:3000" : appOrigin;
        URI origin = URI.create(appOrigin);
        if (!java.util.Set.of("http", "https").contains(origin.getScheme())
                || origin.getHost() == null
                || origin.getUserInfo() != null
                || origin.getQuery() != null
                || origin.getFragment() != null
                || !(origin.getPath().isEmpty() || origin.getPath().equals("/"))) {
            throw new IllegalArgumentException("슈팅 게임의 공개 Origin 설정이 올바르지 않습니다.");
        }
        // URL.origin과 같은 정규화로 프론트·게임 서버의 접속 증명 기준을 일치시킨다.
        int port = origin.getPort();
        boolean defaultPort =
                port == -1
                        || origin.getScheme().equals("http") && port == 80
                        || origin.getScheme().equals("https") && port == 443;
        appOrigin =
                origin.getScheme()
                        + "://"
                        + origin.getHost().toLowerCase(java.util.Locale.ROOT)
                        + (defaultPort ? "" : ":" + port);
        boolean local =
                java.util.Set.of("localhost", "127.0.0.1", "[::1]").contains(origin.getHost())
                        && port == 3000;
        publicUrl =
                publicUrl == null || publicUrl.isBlank()
                        ? local
                                ? origin.getScheme().replaceFirst("^http", "ws")
                                        + "://"
                                        + origin.getHost()
                                        + ":9083"
                                : appOrigin.replaceFirst("^http", "ws") + "/game-realtime"
                        : publicUrl.replaceAll("/$", "");
        URI endpoint = URI.create(publicUrl);
        if (!java.util.Set.of("ws", "wss").contains(endpoint.getScheme())
                || endpoint.getHost() == null
                || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null
                || endpoint.getFragment() != null
                || origin.getScheme().equals("https") && !endpoint.getScheme().equals("wss")) {
            throw new IllegalArgumentException("슈팅 게임 WebSocket 주소가 올바르지 않습니다.");
        }
        if (enabled) {
            try {
                byte[] key = Base64.getDecoder().decode(serviceSecret);
                if (key.length != 32
                        || !Base64.getEncoder().encodeToString(key).equals(serviceSecret))
                    throw new IllegalArgumentException();
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("게임 서버에는 별도 32바이트 Base64 인증 키가 필요합니다.");
            }
        }
    }
}

package com.project.playground.domain.shooting.service;

import com.project.playground.auth.repository.RedisSessionRepository;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** 로비의 변경 여부만 전파한다. 슈팅 방 정보·비밀번호는 전송하지 않고 기존 인증 검색 API를 사용한다. */
@Component
@RequiredArgsConstructor
public class ShootingLobbyEvents implements MessageListener {
    public static final String TOPIC = "shooting:lobby:changed:v1";
    private static final int MAX_SUBSCRIPTIONS_PER_USER = 4;
    private static final int MAX_LOCAL_SUBSCRIPTIONS = 4_000;

    private final RedisSessionRepository sessions;
    private final ConcurrentMap<UUID, Subscription> subscriptions = new ConcurrentHashMap<>();
    // Redis 메시지 처리 스레드를 SSE 네트워크 전송으로 막지 않는다.
    private final ExecutorService writes = Executors.newVirtualThreadPerTaskExecutor();

    public synchronized SseEmitter subscribe(Long userId, UUID sessionId) {
        if (subscriptions.size() >= MAX_LOCAL_SUBSCRIPTIONS
                || subscriptions.values().stream().filter(s -> s.userId.equals(userId)).count() >= MAX_SUBSCRIPTIONS_PER_USER) {
            throw new BusinessException(ErrorCode.ROOM_RATE_LIMIT_EXCEEDED);
        }
        UUID id = UUID.randomUUID();
        // 로비는 대국 SSE와 달리 주기적인 강제 재접속이 필요 없다. 네트워크 유휴 종료는 heartbeat로 방지한다.
        SseEmitter emitter = new SseEmitter(0L);
        Subscription subscription = new Subscription(userId, sessionId, emitter);
        subscriptions.put(id, subscription);
        Runnable remove = () -> subscriptions.remove(id, subscription);
        emitter.onCompletion(remove);
        emitter.onTimeout(() -> { remove.run(); emitter.complete(); });
        emitter.onError(error -> remove.run());
        // 구독 등록 후 ready를 보내기 때문에 최초 조회와 구독 사이에서 발생한 변경은 클라이언트가 재확인한다.
        dispatch(subscription, true);
        return emitter;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (TOPIC.equals(new String(message.getChannel(), java.nio.charset.StandardCharsets.UTF_8))) notifySubscribers();
    }

    private void notifySubscribers() { subscriptions.values().forEach(s -> { s.pending.set(true); dispatch(s, false); }); }

    // 연결을 유지하고 로그아웃·세션 만료를 차단한다. Redis 세션만 확인하며 MySQL 방 목록은 조회하지 않는다.
    @Scheduled(fixedDelay = 25_000)
    public void heartbeat() { subscriptions.values().forEach(s -> dispatch(s, false)); }

    private void dispatch(Subscription subscription, boolean ready) {
        if (!subscription.sending.compareAndSet(false, true)) return;
        writes.execute(() -> {
            try {
                if (sessions.findBySessionId(subscription.sessionId, subscription.userId).isEmpty()) {
                    subscriptions.values().remove(subscription);
                    subscription.emitter.complete();
                    return;
                }
                boolean changed = subscription.pending.getAndSet(false);
                subscription.emitter.send(ready ? SseEmitter.event().name("ready").data("connected")
                        : changed ? SseEmitter.event().name("changed").data("refresh")
                        : SseEmitter.event().comment("heartbeat"));
            } catch (IOException | IllegalStateException exception) {
                // 클라이언트가 종료한 응답은 Servlet 컨테이너가 회수한다.
                subscriptions.values().remove(subscription);
            } catch (RuntimeException exception) {
                subscriptions.values().remove(subscription);
                subscription.emitter.complete();
            } finally {
                subscription.sending.set(false);
                // 전송 중 여러 변경이 들어왔으면 하나로 묶되 최종 변경 알림을 버리지 않는다.
                if (subscription.pending.get() && subscriptions.containsValue(subscription)) dispatch(subscription, false);
            }
        });
    }

    @PreDestroy
    public void close() {
        subscriptions.values().forEach(s -> s.emitter.complete());
        writes.close();
    }


    private static final class Subscription {
        private final Long userId;
        private final UUID sessionId;
        private final SseEmitter emitter;
        private final AtomicBoolean sending = new AtomicBoolean();
        private final AtomicBoolean pending = new AtomicBoolean();

        private Subscription(Long userId, UUID sessionId, SseEmitter emitter) {
            this.userId = userId;
            this.sessionId = sessionId;
            this.emitter = emitter;
        }
    }
}

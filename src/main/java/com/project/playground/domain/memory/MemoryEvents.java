package com.project.playground.domain.memory;


import com.project.playground.auth.repository.RedisSessionRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** 커밋 이후 Redis로 방 변경 알림만 전달한다. 보호된 판 정보는 매번 인증·참가 검증을 거쳐 조회한다. */
@Component @RequiredArgsConstructor
public class MemoryEvents implements MessageListener {
    public static final String TOPIC = "memory:game:changed:v1";
    public static final String CLOCK_TOPIC = "memory:game:clock:v1";
    private final StringRedisTemplate redis;
    private final ApplicationEventPublisher publisher;
    private final RedisSessionRepository sessions;
    private final ConcurrentMap<UUID, Subscription> subscriptions = new ConcurrentHashMap<>();
    // 느린 수신자가 Redis 수신 스레드를 멈추지 않도록 가상 스레드에서 전송하고 연결당 알림을 합친다.
    private final ExecutorService writes = Executors.newVirtualThreadPerTaskExecutor();

    public void changed(Long roomId) { publisher.publishEvent(new Changed(roomId)); }
    /** 게임과 접속 종료가 같은 방 마감 예약을 공유하므로 새 전역 DB 폴링을 만들지 않는다. */
    public void clockChanged(Long roomId, Instant deadline) {
        publisher.publishEvent(ClockChanged.builder().roomId(roomId).deadline(deadline).build());
    }
    @TransactionalEventListener
    public void clockCommitted(ClockChanged event) {
        try { redis.convertAndSend(CLOCK_TOPIC, event.getRoomId() + "|" + (event.getDeadline() == null ? "" : event.getDeadline())); }
        catch (RuntimeException ignored) { /* 같은 서버의 커밋 리스너가 예약을 유지하고 Redis 재접속 때 다른 서버도 복구한다. */ }
    }
    @TransactionalEventListener
    public void committed(Changed event) {
        try { redis.convertAndSend(TOPIC, event.roomId.toString()); }
        catch (RuntimeException ignored) { notifyRoom(event.roomId); } // 주기적 인증 조회도 유실 알림을 복구한다.
    }
    public synchronized SseEmitter subscribe(Long userId, UUID sessionId, Long roomId, long revision) {
        long count = subscriptions.values().stream().filter(s -> s.userId.equals(userId)).count();
        if (count >= 4 || subscriptions.size() >= 4000) throw new com.project.playground.global.exception.BusinessException(
                com.project.playground.global.exception.ErrorCode.ROOM_RATE_LIMIT_EXCEEDED);
        var id = UUID.randomUUID();
        var emitter = new SseEmitter(20_000L);
        var subscription = new Subscription(userId, sessionId, roomId, revision, emitter);
        subscriptions.put(id, subscription);
        Runnable remove = () -> subscriptions.remove(id);
        emitter.onCompletion(remove); emitter.onTimeout(() -> { remove.run(); emitter.complete(); }); emitter.onError(error -> remove.run());
        send(subscription, false);
        return emitter;
    }
    @Override public void onMessage(Message message, byte[] pattern) {
        if ("auth:access-policy:changed:v1".equals(new String(message.getChannel(), StandardCharsets.UTF_8))) {
            heartbeat();
            return;
        }
        try { notifyRoom(Long.valueOf(new String(message.getBody(), StandardCharsets.UTF_8))); }
        catch (NumberFormatException ignored) { /* 내부 토픽도 올바른 방 번호만 처리한다. */ }
    }
    private void notifyRoom(Long roomId) { subscriptions.values().stream().filter(s -> s.roomId.equals(roomId)).forEach(s -> send(s, true)); }
    // 이벤트가 없는 대국도 제한을 빠르게 반영한다. 이 검사는 세션 수명을 연장하지 않는다.
    @Scheduled(fixedDelay = 2_000) public void heartbeat() { subscriptions.values().forEach(s -> send(s, false)); }
    private void send(Subscription subscription, boolean changed) {
        // Heartbeat 전송/인증 확인과 Redis 변경 알림이 겹쳐도 변경 신호를 버리지 않는다.
        // 알림에는 방 데이터가 없으므로 같은 연결의 여러 변경은 한 번의 조회 신호로 합친다.
        if (changed) subscription.changedPending.set(true);
        if (!subscription.sending.compareAndSet(false, true)) return;
        writes.execute(() -> {
            try {
                do {
                    boolean notifyChanged = subscription.changedPending.getAndSet(false);
                    boolean notifyReady = subscription.readyPending.getAndSet(false);
                    if (sessions.findBySessionId(subscription.sessionId, subscription.userId).isEmpty()) {
                        subscription.emitter.complete();
                        subscriptions.values().remove(subscription);
                        return;
                    }
                    subscription.emitter.send(notifyChanged
                            ? SseEmitter.event().name("changed").data("refresh")
                            : notifyReady ? SseEmitter.event().name("ready").data(subscription.revision)
                            : SseEmitter.event().comment("heartbeat"));
                    // 변경 알림이 전송 중 도착했다면 Heartbeat보다 우선해 바로 이어서 알린다.
                } while (subscription.changedPending.get());
            }
            catch (IOException | IllegalStateException error) {
                // 끊긴 응답의 ASYNC 오류 종료는 Servlet 컨테이너가 담당한다. 중복 completeWithError를 호출하지 않는다.
                subscriptions.values().remove(subscription);
            }
            catch (RuntimeException error) {
                // 정책 제한·저장소 장애 때 알림 전송을 중단한다. 재연결 요청에서 인증 오류와 안내를 전달한다.
                subscriptions.values().remove(subscription);
                subscription.emitter.complete();
            }
            finally {
                subscription.sending.set(false);
                // 루프가 종료되는 순간 새 이벤트가 들어오는 경합도 다시 전송한다.
                if (subscription.changedPending.get() && subscriptions.containsValue(subscription)) send(subscription, false);
            }
        });
    }
    @PreDestroy public void close() { subscriptions.values().forEach(s -> s.emitter.complete()); writes.close(); }
    // 이벤트에는 방 번호만 보관한다. DTO나 비밀번호·인증 토큰은 이벤트에 넣지 않는다.
    public static final class Changed { private final Long roomId; private Changed(Long roomId) { this.roomId = roomId; } }
    // 인증·판 정보를 싣지 않는 내부 예약 이벤트다. null 마감은 종료 상태를 한 번 확인하라는 뜻이다.
    @Getter @Builder
    public static final class ClockChanged { private final Long roomId; private final Instant deadline; }
    private static final class Subscription {
        private final Long userId; private final UUID sessionId; private final Long roomId; private final long revision; private final SseEmitter emitter;
        private final AtomicBoolean sending = new AtomicBoolean();
        private final AtomicBoolean changedPending = new AtomicBoolean();
        // 최초 연결에만 보낼 최신 revision이다. 일치하면 클라이언트는 DB를 다시 읽지 않는다.
        private final AtomicBoolean readyPending = new AtomicBoolean(true);
        private Subscription(Long userId, UUID sessionId, Long roomId, long revision, SseEmitter emitter) {
            this.userId = userId; this.sessionId = sessionId; this.roomId = roomId; this.revision = revision; this.emitter = emitter;
        }
    }
}


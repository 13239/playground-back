package com.project.playground.domain.activitylog.service;

import com.project.playground.domain.activitylog.entity.ActivityLog;
import com.project.playground.domain.activitylog.event.ActivityLogEvent;
import com.project.playground.domain.activitylog.repository.ActivityLogRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 활동 로그를 요청 스레드와 분리된 전용 스레드에서 저장한다.
 * 전역 TaskExecutor Bean을 등록하지 않아 Spring MVC 등 다른 비동기 처리의 기본 실행기를 바꾸지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityLogWriter {

    // 로그 저장이 DB 커넥션 풀을 독점해 로그인·가입 요청이 커넥션을 기다리지 않도록 저장 스레드 수를 제한한다.
    private static final int WORKER_COUNT = 2;

    // DB 장애 시 대기 중인 로그가 메모리를 계속 차지하지 않도록 대기열 크기를 제한한다.
    private static final int QUEUE_CAPACITY = 1_000;

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private final ActivityLogRepository activityLogRepository;

    private final JsonMapper jsonMapper;

    // 대기열이 가득 차거나 종료 중이면 예외 대신 경고만 남긴다. 커밋 이후 콜백의 예외가 성공한 요청을 실패 응답으로 바꾸지 않게 한다.
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            WORKER_COUNT, WORKER_COUNT, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(QUEUE_CAPACITY),
            Thread.ofPlatform().name("activity-log-", 0).daemon(true).factory(),
            (task, pool) -> log.warn("활동 로그 저장 대기열을 사용할 수 없어 로그를 저장하지 못했습니다."));

    public void write(ActivityLogEvent event) {
        executor.execute(() -> save(event));
    }

    // 저장 실패는 요청 결과를 바꾸지 않는다. IP·User-Agent 같은 개인정보는 애플리케이션 로그에 남기지 않는다.
    private void save(ActivityLogEvent event) {
        try {
            activityLogRepository.save(toEntity(event));
        } catch (RuntimeException e) {
            log.warn("활동 로그 저장 실패. type={}, result={}", event.getType(), event.getResult(), e);
        }
    }

    private ActivityLog toEntity(ActivityLogEvent event) {
        return ActivityLog.builder()
                .type(event.getType())
                .result(event.getResult())
                .failureCode(event.getFailureCode())
                .userId(event.getUserId())
                .clientIp(event.getClientIp())
                .userAgent(event.getUserAgent())
                .detail(event.getDetail().isEmpty() ? null : jsonMapper.writeValueAsString(event.getDetail()))
                .occurredAt(event.getOccurredAt())
                .build();
    }

    // 서버 종료 시 대기 중인 로그를 제한 시간 동안 저장하고, 남은 작업은 버린 건수만 남긴다.
    @PreDestroy
    public void close() {
        executor.shutdown();

        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("종료 시간 안에 저장하지 못한 활동 로그 {}건을 버렸습니다.", executor.shutdownNow().size());
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

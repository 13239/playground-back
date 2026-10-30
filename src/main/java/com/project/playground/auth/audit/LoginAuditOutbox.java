package com.project.playground.auth.audit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.*;

/** DB 장애 중에도 로컬 디스크에 먼저 기록하고 복구 후 같은 활동 로그로 재전송한다. */
@Component @Slf4j
public class LoginAuditOutbox {
    private final Path directory;
    private final LoginAuditStore store;
    private final JsonMapper json;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("login-audit-").daemon(true).factory());
    private static final int MAX_PENDING = 10000;

    public LoginAuditOutbox(LoginAuditStore store, JsonMapper json,
            @Value("${LOGIN_AUDIT_OUTBOX_DIR:.data/login-audit}") String directory) {
        this.store = store;
        this.json = json;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }
    @PostConstruct void start() { worker.scheduleWithFixedDelay(this::drain, 1, 5, TimeUnit.SECONDS); }
    public synchronized void enqueue(LoginAuditRecord record) {
        LoginAuditRecord selected = null;
        try {
            selected = LoginAuditSanitizer.record(record);
            record = selected;
            Files.createDirectories(directory);
            privatePermissions(directory, true);
            try (var files = Files.list(directory)) {
                if (files.limit(MAX_PENDING + 1L).count() >= MAX_PENDING) throw new IllegalStateException("Audit outbox capacity reached");
            }
            String filename = UUID.fromString(record.eventId()).toString();
            byte[] bytes = json.writeValueAsBytes(record);
            if (bytes.length > 32768) throw new IllegalArgumentException("Audit record too large");
            Path pending = directory.resolve(filename + ".pending"), target = directory.resolve(filename + ".json");
            Files.write(pending, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            privatePermissions(pending, false);
            try { Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception failure) {
            if (selected == null) { log.error("LOGIN_AUDIT_INVALID_RECORD_DROPPED"); return; }
            // 이 기록은 이미 비밀값을 제외한 필드 집합이다. DB·디스크가 모두 실패한 경우 최후의 서버 로그를 남긴다.
            try { log.error("LOGIN_AUDIT_FALLBACK eventId={} detail={}", record.eventId(), json.writeValueAsString(record)); }
            catch (RuntimeException serializationFailure) { log.error("LOGIN_AUDIT_SERIALIZATION_FAILED eventId={}", record.eventId()); }
        }
    }
    private void drain() {
        try {
            if (!Files.isDirectory(directory)) return;
            List<Path> files;
            try (var stream = Files.list(directory)) { files = stream.filter(this::deliverable).sorted().limit(20).toList(); }
            for (var file : files) {
                try {
                    if (Files.size(file) > 32768) {
                        Files.move(file, file.resolveSibling(file.getFileName() + ".invalid"), StandardCopyOption.REPLACE_EXISTING);
                        log.error("LOGIN_AUDIT_INVALID_FILE {}", file.getFileName()); continue;
                    }
                    LoginAuditRecord record;
                    try { record = LoginAuditSanitizer.record(json.readValue(Files.readAllBytes(file), LoginAuditRecord.class)); }
                    catch (RuntimeException invalid) {
                        Files.move(file, file.resolveSibling(file.getFileName() + ".invalid"), StandardCopyOption.REPLACE_EXISTING);
                        log.error("LOGIN_AUDIT_INVALID_RECORD {}", file.getFileName());
                        continue;
                    }
                    try { store.save(record); }
                    catch (DataIntegrityViolationException duplicate) {
                        // 병렬 전송의 고유 ID 충돌만 다음 저장에서 존재 확인 후 제거한다.
                        store.save(record);
                    }
                    Files.deleteIfExists(file);
                } catch (Exception unavailable) { log.warn("LOGIN_AUDIT_RETRY_PENDING"); break; }
            }
        } catch (Exception unavailable) { log.warn("LOGIN_AUDIT_OUTBOX_UNAVAILABLE"); }
    }
    private boolean deliverable(Path path) {
        String name = path.getFileName().toString();
        if (name.matches("[a-f0-9-]{36}\\.json")) return true;
        if (!name.matches("[a-f0-9-]{36}\\.pending")) return false;
        try { return Files.getLastModifiedTime(path).toMillis() < System.currentTimeMillis() - 60000; }
        catch (Exception ignored) { return false; }
    }
    private static void privatePermissions(Path path, boolean directory) {
        try { Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------")); }
        catch (Exception ignored) { /* Windows에서는 현재 사용자 파일 ACL을 사용한다. */ }
    }
    @PreDestroy void stop() { worker.shutdownNow(); }
}

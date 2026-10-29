package com.project.playground.domain.shooting.service;

import com.project.playground.auth.proof.BrowserProof;
import com.project.playground.auth.repository.RedisSessionRepository;
import com.project.playground.domain.game.service.GameAvailabilityService;
import com.project.playground.domain.shooting.config.ShootingProperties;
import com.project.playground.domain.shooting.dto.*;
import com.project.playground.domain.shooting.repository.ShootingRecordRepository;
import com.project.playground.domain.user.repository.reader.UserReader;
import com.project.playground.global.exception.*;
import com.project.playground.global.security.session.Session;

import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.stream.IntStream;

/** 기존 로그인·운영 정책을 게임 참가와 재접속에 재사용하며 순위는 인증된 게임 서버 결과만 받는다. */
@Service
@RequiredArgsConstructor
public class ShootingService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final RedisScript<Long> RATE =
            RedisScript.of(
                    "local n=redis.call('INCR',KEYS[1]); if n==1 then"
                        + " redis.call('PEXPIRE',KEYS[1],60000) end; return n",
                    Long.class);
    private static final RedisScript<Long> REFRESH =
            RedisScript.of(
                    "if redis.call('GET',KEYS[1])==ARGV[1] then"
                        + " redis.call('PEXPIRE',KEYS[1],45000); return 1 end; return 0",
                    Long.class);
    private static final RedisScript<Long> RELEASE =
            RedisScript.of(
                    "if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('DEL',KEYS[1])"
                        + " end; return 0",
                    Long.class);
    private final ShootingProperties properties;
    private final StringRedisTemplate redis;
    private final RedisSessionRepository sessions;
    private final UserReader users;
    private final GameAvailabilityService availability;
    private final ShootingRecordRepository records;
    private final JsonMapper json;

    public ShootingResponse.Ticket ticket(Session session, ShootingRequest.Ticket request) {
        requireEnabled();
        rate(session.getId(), "ticket", 20);
        availability.requireNewPlay("SHOOTING", request.getMode());
        var user =
                users.findActiveById(session.getId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SESSION));
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var identity =
                ShootingResponse.Identity.builder()
                        .userId(session.getId())
                        .sessionId(session.getSessionId().toString())
                        .browserKey(session.getBrowserKeyThumbprint())
                        .nickname(user.getNickname())
                        .avatar(user.getProfileAvatar().name())
                        .mode(request.getMode())
                        .origin(properties.appOrigin())
                        .proofRequired(
                                !BrowserProof.DEV_HTTP_BINDING.equals(
                                        session.getBrowserKeyThumbprint()))
                        .build();
        redis.opsForValue()
                .set(ticketKey(token), json.writeValueAsString(identity), Duration.ofSeconds(30));
        return ShootingResponse.Ticket.builder()
                .ticket(token)
                .endpoint(properties.publicUrl())
                .expiresIn(30_000)
                .proofRequired(identity.isProofRequired())
                .build();
    }

    public ShootingResponse.Identity consume(ShootingRequest.Consume request) {
        String value = redis.opsForValue().getAndDelete(ticketKey(request.getTicket()));
        if (value == null) throw new BusinessException(ErrorCode.SHOOTING_TICKET_INVALID);
        var identity = json.readValue(value, ShootingResponse.Identity.class);
        validate(identity.getUserId(), identity.getSessionId(), identity.getBrowserKey());
        availability.requireNewPlay("SHOOTING", identity.getMode());
        return identity;
    }

    public void claim(ShootingRequest.Lease request) {
        validate(request.getUserId(), request.getSessionId(), request.getBrowserKey());
        if (!Boolean.TRUE.equals(
                redis.opsForValue()
                        .setIfAbsent(
                                leaseKey(request), leaseValue(request), Duration.ofSeconds(45))))
            throw new BusinessException(ErrorCode.SHOOTING_ALREADY_PLAYING);
    }

    public void validateLease(ShootingRequest.Lease request) {
        validate(request.getUserId(), request.getSessionId(), request.getBrowserKey());
        if (!Long.valueOf(1)
                .equals(redis.execute(REFRESH, List.of(leaseKey(request)), leaseValue(request))))
            throw new BusinessException(ErrorCode.SHOOTING_TICKET_INVALID);
    }

    public void release(ShootingRequest.Lease request) {
        // 이전 방의 지연된 종료가 새 방의 사용권을 삭제하지 않도록 값을 비교한 뒤 삭제한다.
        redis.execute(RELEASE, List.of(leaseKey(request)), leaseValue(request));
    }

    @Transactional
    public void checkpoint(ShootingRequest.Checkpoint request) {
        sessions.findBySessionId(UUID.fromString(request.getSessionId()), request.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SESSION));
        String id = request.getRunId() + "-" + request.getUserId();
        records.ensure(id, request.getRunId(), request.getUserId(), request.getMode());
        var record = records.lock(id).orElseThrow();
        if (!record.getMode().equals(request.getMode()))
            throw new BusinessException(ErrorCode.SHOOTING_RECORD_INVALID);
        record.checkpoint(
                request.getClearedStage(),
                request.getCombatMillis(),
                request.getTeammate(),
                request.isFinished());
    }

    @Transactional(readOnly = true)
    public ShootingResponse.Overview overview(Long userId, ShootingRequest.Overview request) {
        rate(userId, "ranking", 90);
        String mode = request.getMode();
        int pages = Math.max(1, (int) Math.ceilDiv(Math.min(100, records.rankedCount(mode)), 10));
        int page = Math.clamp(request.getPage(), 1, pages), offset = (page - 1) * 10;
        var rows = records.ranking(mode, offset);
        var own =
                records.findFirstByUserIdAndModeOrderByStageDescCombatMillisAsc(userId, mode)
                        .orElse(null);
        var ranking =
                IntStream.range(0, rows.size())
                        .mapToObj(
                                index -> {
                                    var row = rows.get(index);
                                    return ShootingResponse.Ranking.builder()
                                            .rank(offset + index + 1)
                                            .nickname(row.getNickname())
                                            .teammate(row.getTeammate())
                                            .stage(row.getStage())
                                            .combatMillis(row.getCombatMillis())
                                            .self(row.getUserId().equals(userId))
                                            .build();
                                })
                        .toList();
        return ShootingResponse.Overview.builder()
                .mode(mode)
                .page(page)
                .totalPages(pages)
                .bestStage(own == null ? 0 : own.getStage())
                .bestMillis(own == null ? 0 : own.getCombatMillis())
                .rankings(ranking)
                .build();
    }

    private void validate(Long userId, String sessionId, String thumbprint) {
        var session =
                sessions.findBySessionId(UUID.fromString(sessionId), userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SESSION));
        if (!Objects.equals(session.getBrowserKeyThumbprint(), thumbprint))
            throw new BusinessException(ErrorCode.SHOOTING_TICKET_INVALID);
    }

    private void requireEnabled() {
        if (!properties.enabled()) throw new BusinessException(ErrorCode.SHOOTING_UNAVAILABLE);
    }

    private void rate(Long userId, String action, int maximum) {
        Long count = redis.execute(RATE, List.of("shooting:limit:" + action + ":" + userId));
        if (count == null || count > maximum)
            throw new BusinessException(ErrorCode.SHOOTING_RATE_LIMIT);
    }

    private String ticketKey(String token) {
        try {
            return "shooting:ticket:"
                    + HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String leaseKey(ShootingRequest.Lease request) {
        return "shooting:active:" + request.getUserId();
    }

    private String leaseValue(ShootingRequest.Lease request) {
        return request.getRoomId() + ":" + request.getSessionId();
    }
}

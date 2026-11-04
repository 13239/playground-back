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
        // 방 코드 권한은 협동 방 한 곳으로만 한정한다. 생성·다른 방 참가로 전용할 수 없다.
        if (request.getInvitedRoomCode() != null
                && (!"COOP".equals(request.getMode()) || !request.getInvitedRoomCode().matches("[A-Z2-9]{8}")))
            throw new BusinessException(ErrorCode.INVALID_REQUEST_BODY);
        rate(session.getId(), "ticket", 20);
        availability.requireNewPlay("SHOOTING", request.getMode());
        String mapId = ShootingStageRules.requireMap(request.getMapId() == null ? "CAVE" : request.getMapId());
        var progress = progress(session.getId());
        if (request.getInvitedRoomCode() == null &&
                (progress.getUnlockedMaps() & (1 << ShootingStageRules.MAPS.indexOf(mapId))) == 0)
            throw new BusinessException(ErrorCode.INVALID_REQUEST_BODY);
        var user =
                users.findActiveById(session.getId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SESSION));
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var identity =
                ShootingResponse.Identity.builder()
                        .mapId(mapId)
                        .unlockedMaps(progress.getUnlockedMaps())
                        .userId(session.getId())
                        .sessionId(session.getSessionId().toString())
                        .browserKey(session.getBrowserKeyThumbprint())
                        .nickname(user.getNickname())
                        .avatar(user.getProfileAvatar().name())
                        .mode(request.getMode())
                        .origin(properties.appOrigin())
                        .invitedRoomCode(request.getInvitedRoomCode())
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

    /** 같은 방의 재도전도 신규 플레이 정책을 다시 확인해 점검·비활성 모드를 우회하지 않는다. */
    public void restart(ShootingRequest.Ticket request) {
        requireEnabled();
        availability.requireNewPlay("SHOOTING", request.getMode());
    }

    @Transactional
    public void checkpoint(ShootingRequest.Checkpoint request) {
        sessions.findBySessionId(UUID.fromString(request.getSessionId()), request.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SESSION));
        String id = request.getRunId() + "-" + request.getUserId();
        String mapId = ShootingStageRules.requireMap(request.getMapId());
        if (request.getRuleVersion() != ShootingStageRules.VERSION ||
                (request.isCompleted() && (!request.isFinished() || request.getClearedStage() != 10 || request.getCombatMillis() <= 0)))
            throw new BusinessException(ErrorCode.SHOOTING_RECORD_INVALID);
        records.ensure(id, request.getRunId(), request.getUserId(), request.getMode(), mapId);
        var record = records.lock(id).orElseThrow();
        if (!record.getMode().equals(request.getMode()) || !record.getMapId().equals(mapId) || record.getRuleVersion() != ShootingStageRules.VERSION)
            throw new BusinessException(ErrorCode.SHOOTING_RECORD_INVALID);
        boolean previouslyFinished = record.isFinished();
        record.checkpoint(
                request.getClearedStage(),
                request.getCombatMillis(),
                request.getTeammate(),
                request.isFinished());
        if (request.isCompleted() && !previouslyFinished)
            record.complete(ShootingStageRules.stars(mapId, request.getCombatMillis()));
    }

    @Transactional(readOnly = true)
    public ShootingResponse.Overview overview(Long userId, ShootingRequest.Overview request) {
        rate(userId, "ranking", 90);
        String mode = request.getMode();
        String mapId = ShootingStageRules.requireMap(request.getMapId());
        int pages = Math.max(1, (int) Math.ceilDiv(Math.min(100, records.rankedCount(mode, mapId)), 10));
        int page = Math.clamp(request.getPage(), 1, pages), offset = (page - 1) * 10;
        var rows = records.ranking(mode, mapId, offset);
        var own =
                records.findFirstByUserIdAndModeAndMapIdAndRuleVersionOrderByStageDescCombatMillisAsc(userId, mode, mapId, ShootingStageRules.VERSION)
                        .orElse(null);
        var ranking =
                IntStream.range(0, rows.size())
                        .mapToObj(
                                index -> {
                                    var row = rows.get(index);
                                    return ShootingResponse.Ranking.builder()
                                            .stars(row.getStars())
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
                .mapId(mapId)
                .mode(mode)
                .page(page)
                .totalPages(pages)
                .bestStage(own == null ? 0 : own.getStage())
                .bestMillis(own == null ? 0 : own.getCombatMillis())
                .rankings(ranking)
                .build();
    }

    /** 어느 한 모드에서 1성만 얻어도 다음 맵을 연다. 동일 맵의 별은 큰 값 하나만 더한다. */
    @Transactional(readOnly = true)
    public ShootingResponse.Progress progress(Long userId) {
        var rows = records.progress(userId);
        var result = new ArrayList<ShootingResponse.MapProgress>();
        int total = 0, unlocked = 1;
        for (int index = 0; index < ShootingStageRules.MAPS.size(); index++) {
            String mapId = ShootingStageRules.MAPS.get(index);
            var single = rows.stream()
                    .filter(row -> mapId.equals(row.getMapId()) && "SINGLE".equals(row.getMode()))
                    .findFirst().orElse(null);
            var coop = rows.stream()
                    .filter(row -> mapId.equals(row.getMapId()) && "COOP".equals(row.getMode()))
                    .findFirst().orElse(null);
            int singleStars = single == null ? 0 : single.getStars();
            int coopStars = coop == null ? 0 : coop.getStars();
            int best = Math.max(singleStars, coopStars);
            boolean open = (unlocked & (1 << index)) != 0;
            result.add(ShootingResponse.MapProgress.builder()
                    .mapId(mapId)
                    .unlocked(open)
                    .stars(best)
                    .singleStars(singleStars)
                    .coopStars(coopStars)
                    .singleMillis(single == null ? 0 : single.getBestMillis())
                    .coopMillis(coop == null ? 0 : coop.getBestMillis())
                    .build());
            total += best;
            if (best > 0 && index < 2) unlocked |= 1 << (index + 1);
        }
        return ShootingResponse.Progress.builder()
                .totalStars(total)
                .unlockedMaps(unlocked)
                .maps(result)
                .build();
    }

    /** 브라우저의 반복 조회만 제한하고 티켓·내부 재도전의 권한 조회와 한도를 공유하지 않는다. */
    @Transactional(readOnly = true)
    public ShootingResponse.Progress publicProgress(Long userId) {
        rate(userId, "progress", 90);
        return progress(userId);
    }

    /** 재도전·다음 맵 출격 직전 실제 세션과 저장된 진행을 다시 확인한다. */
    public ShootingResponse.Progress internalProgress(ShootingRequest.Lease request) {
        validateLease(request);
        return progress(request.getUserId());
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

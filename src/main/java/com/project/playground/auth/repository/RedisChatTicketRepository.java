package com.project.playground.auth.repository;

import com.project.playground.auth.chat.ChatTicketProperties;
import com.project.playground.auth.chat.ChatTicketRateLimitException;
import com.project.playground.auth.dto.response.ChatTicketResponse;
import com.project.playground.domain.user.dto.response.UserProfileResponse;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import com.project.playground.global.security.session.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/** DB 조회 전 예약과 최종 발급을 분리하고 각 단계의 세션 검증·상태 변경을 Lua로 원자적으로 처리한다. */
@Repository
@RequiredArgsConstructor
public class RedisChatTicketRepository {

    private static final RedisScript<List> ISSUE = RedisScript.of(
            new ClassPathResource("redis/chat/issue-ticket.lua"), List.class);
    private static final RedisScript<List> RESERVE = RedisScript.of(
            new ClassPathResource("redis/chat/reserve-ticket.lua"), List.class);
    private static final RedisScript<Long> RELEASE_RESERVATION = RedisScript.of(
            new ClassPathResource("redis/chat/release-ticket-reservation.lua"), Long.class);

    private final StringRedisTemplate redisTemplate;

    /** 유효한 세션의 발급 시도만 차감하고 예약과 실제 티켓을 합친 대기 상한을 DB 조회 전에 확인한다. */
    public void reserve(String ticketHash, Session session) {
        String sid = session.getSessionId().toString();
        String userId = session.getId().toString();
        List<?> result = redisTemplate.execute(RESERVE, List.of(
                "auth:session:" + sid,
                "auth:user:sessions:" + userId,
                "auth:account:blocked:" + userId,
                "chat:auth:ticket:" + ticketHash,
                "chat:auth:pending:" + sid,
                "chat:auth:limit:" + userId
        ), sid, userId, session.getBrowserKeyThumbprint(), ticketHash,
                Long.toString(ChatTicketProperties.RESERVATION_TTL_MILLIS),
                Integer.toString(ChatTicketProperties.MAX_PENDING_PER_SESSION),
                Integer.toString(ChatTicketProperties.MAX_ATTEMPTS_PER_MINUTE),
                Long.toString(ChatTicketProperties.TICKET_TTL_MILLIS));
        requireSuccess(result);
    }

    /** 반환은 reservation: 접두사의 해당 항목에만 적용하며 실제 티켓이나 발급 횟수를 삭제하지 않는다. */
    public void releaseReservation(String ticketHash, Session session) {
        Long result = redisTemplate.execute(RELEASE_RESERVATION,
                List.of("chat:auth:pending:" + session.getSessionId()), ticketHash);
        if (result == null) throw new BusinessException(ErrorCode.CHAT_SERVICE_UNAVAILABLE);
    }

    public ChatTicketResponse issue(
            String ticket, String ticketHash, Session session,
            UserProfileResponse profile, ChatTicketProperties properties) {
        String sid = session.getSessionId().toString();
        String userId = session.getId().toString();
        List<?> result = redisTemplate.execute(ISSUE, List.of(
                "auth:session:" + sid,
                "auth:user:sessions:" + userId,
                "auth:account:blocked:" + userId,
                "chat:auth:ticket:" + ticketHash,
                "chat:auth:pending:" + sid
        ), sid, userId, session.getBrowserKeyThumbprint(), ticketHash,
                profile.getNickname(), profile.getAvatarId().name(),
                properties.getOrigin(), properties.getWebsocketUrl(),
                Long.toString(ChatTicketProperties.TICKET_TTL_MILLIS));
        requireSuccess(result);
        return ChatTicketResponse.builder()
                .ticket(ticket)
                .serverTime(Instant.ofEpochMilli(Long.parseLong(String.valueOf(result.get(1)))))
                .expiresAt(Instant.ofEpochMilli(Long.parseLong(String.valueOf(result.get(2)))))
                .websocketUrl(properties.getWebsocketUrl())
                .proofRequired(properties.isProofRequired())
                .build();
    }

    // 예약·최종 발급은 기존 오류 코드와 Retry-After 계약을 공유한다.
    private void requireSuccess(List<?> result) {
        if (result == null || result.size() != 3) {
            throw new BusinessException(ErrorCode.CHAT_SERVICE_UNAVAILABLE);
        }
        String status = String.valueOf(result.getFirst());
        if ("SESSION".equals(status)) throw new BusinessException(ErrorCode.INVALID_SESSION);
        if ("LIMIT".equals(status)) {
            throw new ChatTicketRateLimitException(Long.parseLong(String.valueOf(result.get(1))));
        }
        if (!"OK".equals(status)) throw new BusinessException(ErrorCode.CHAT_SERVICE_UNAVAILABLE);
    }
}

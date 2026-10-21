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

/** 로그인 세션 재검증·발급 제한·TTL 저장을 하나의 Lua 실행으로 처리한다. */
@Repository
@RequiredArgsConstructor
public class RedisChatTicketRepository {

    private static final RedisScript<List> ISSUE = RedisScript.of(
            new ClassPathResource("redis/chat/issue-ticket.lua"), List.class);

    private final StringRedisTemplate redisTemplate;

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
                "chat:auth:pending:" + sid,
                "chat:auth:limit:" + userId
        ), sid, userId, session.getBrowserKeyThumbprint(), ticketHash,
                profile.getNickname(), profile.getAvatarId().name(),
                properties.getOrigin(), properties.getWebsocketUrl(),
                Long.toString(ChatTicketProperties.TICKET_TTL_MILLIS),
                Integer.toString(ChatTicketProperties.MAX_PENDING_PER_SESSION),
                Integer.toString(ChatTicketProperties.MAX_ISSUES_PER_MINUTE));
        if (result == null || result.size() != 3) {
            throw new BusinessException(ErrorCode.CHAT_SERVICE_UNAVAILABLE);
        }
        String status = String.valueOf(result.getFirst());
        if ("SESSION".equals(status)) throw new BusinessException(ErrorCode.INVALID_SESSION);
        if ("LIMIT".equals(status)) {
            throw new ChatTicketRateLimitException(Long.parseLong(String.valueOf(result.get(1))));
        }
        if (!"OK".equals(status)) throw new BusinessException(ErrorCode.CHAT_SERVICE_UNAVAILABLE);
        return ChatTicketResponse.builder()
                .ticket(ticket)
                .serverTime(Instant.ofEpochMilli(Long.parseLong(String.valueOf(result.get(1)))))
                .expiresAt(Instant.ofEpochMilli(Long.parseLong(String.valueOf(result.get(2)))))
                .websocketUrl(properties.getWebsocketUrl())
                .proofRequired(properties.isProofRequired())
                .build();
    }
}

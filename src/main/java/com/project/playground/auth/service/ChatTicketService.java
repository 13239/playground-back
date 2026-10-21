package com.project.playground.auth.service;

import com.project.playground.auth.chat.ChatTicketProperties;
import com.project.playground.auth.dto.response.ChatTicketResponse;
import com.project.playground.auth.repository.RedisChatTicketRepository;
import com.project.playground.domain.user.service.UserProfileService;
import com.project.playground.global.security.session.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** BFF·JWT·브라우저 증명으로 확인한 사용자에게만 별도의 무작위 채팅 접속 증명을 발급한다. */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatTicketService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final RedisChatTicketRepository tickets;
    private final UserProfileService profiles;
    private final ChatTicketProperties properties;

    public ChatTicketResponse issue(Session session) {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String ticketHash = hash(ticket);
        // DB 접근 전에 사용자 발급 예산과 세션별 대기 자리를 원자적으로 확보한다.
        tickets.reserve(ticketHash, session);
        try {
            // 클라이언트가 보낸 닉네임·이미지를 사용하지 않고 ACTIVE 계정의 실제 프로필을 조회한다.
            var profile = profiles.getProfileForChatTicket(session.getId());
            // 프로필 조회 중 로그아웃·차단·키 변경이 발생했는지 최종 발급 Lua에서 다시 검증한다.
            return tickets.issue(ticket, ticketHash, session, profile, properties);
        } catch (RuntimeException exception) {
            try {
                // 예약만 반환하며 이미 발급된 티켓과 사용자 발급 시도 예산은 변경하지 않는다.
                tickets.releaseReservation(ticketHash, session);
            } catch (RuntimeException cleanupFailure) {
                // 정리 실패가 원래 오류를 덮지 않게 하고 세션·증명·저장소 예외 원문은 기록하지 않는다.
                log.warn("채팅 티켓 예약을 즉시 반환하지 못했습니다. 예약 만료를 기다립니다.");
            }
            throw exception;
        }
    }

    public static String hash(String ticket) {
        try {
            // 원문 증명은 응답 메모리에만 두고 Redis 키에는 SHA-256 해시만 저장한다.
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(ticket.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}

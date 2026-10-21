package com.project.playground.auth.service;

import com.project.playground.auth.chat.ChatTicketProperties;
import com.project.playground.auth.dto.response.ChatTicketResponse;
import com.project.playground.auth.repository.RedisChatTicketRepository;
import com.project.playground.domain.user.service.UserProfileService;
import com.project.playground.global.security.session.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** BFF·JWT·브라우저 증명으로 확인한 사용자에게만 별도의 무작위 채팅 접속 증명을 발급한다. */
@Service
@RequiredArgsConstructor
public class ChatTicketService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final RedisChatTicketRepository tickets;
    private final UserProfileService profiles;
    private final ChatTicketProperties properties;

    public ChatTicketResponse issue(Session session) {
        // 클라이언트가 보낸 닉네임·이미지를 사용하지 않고 ACTIVE 계정의 실제 프로필을 조회한다.
        var profile = profiles.getProfile(session.getId());
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        return tickets.issue(ticket, hash(ticket), session, profile, properties);
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

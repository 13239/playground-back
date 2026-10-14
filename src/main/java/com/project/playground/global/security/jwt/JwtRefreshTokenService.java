package com.project.playground.global.security.jwt;

import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class JwtRefreshTokenService {

    private static final int RANDOM_BYTE_LENGTH = 32;

    private static final int TOKEN_BYTE_LENGTH = 48;

    private final SecureRandom secureRandom = new SecureRandom();

    // 세션 ID(16바이트)와 무작위 값(32바이트)으로 Refresh Token 생성
    public String create(UUID sessionId) {
        byte[] random = new byte[RANDOM_BYTE_LENGTH];
        secureRandom.nextBytes(random);

        byte[] bytes = ByteBuffer.allocate(TOKEN_BYTE_LENGTH)
                .putLong(sessionId.getMostSignificantBits())
                .putLong(sessionId.getLeastSignificantBits())
                .put(random)
                .array();

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    // Refresh Token에 포함된 세션 ID 추출
    public UUID extractSessionId(String refreshToken) {
        if (refreshToken == null || refreshToken.length() != 64) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        try {
            byte[] bytes = Base64.getUrlDecoder().decode(refreshToken);

            if (bytes.length != TOKEN_BYTE_LENGTH) {
                throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
            }

            ByteBuffer buffer = ByteBuffer.wrap(bytes);

            return new UUID(buffer.getLong(), buffer.getLong());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    // Redis에는 Refresh Token 원본 대신 SHA-256 해시를 저장
    public String hash(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hashed = digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}

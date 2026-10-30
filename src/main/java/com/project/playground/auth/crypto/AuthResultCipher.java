package com.project.playground.auth.crypto;

import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Cipher;
import javax.crypto.KDF;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.HKDFParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JWT 키에서 용도가 분리된 키를 파생하여 짧게 공유할 로그인·갱신 결과를 AES-256-GCM으로 보호한다.
 */
@Component
public class AuthResultCipher {

    private static final int IV_BYTES = 12;

    private final SecretKey encryptionKey;

    private final JsonMapper jsonMapper;

    private final SecureRandom secureRandom = new SecureRandom();

    public AuthResultCipher(@Qualifier("jwtSecretKey") SecretKey jwtSecretKey, JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;

        try {
            // Java 25의 표준 HKDF를 사용하며 JWT 서명 키 자체를 AES 키로 재사용하지 않는다.
            // 파생 정보는 기존 갱신 결과와의 호환성을 유지하며 로그인은 별도의 AAD 문맥으로 분리한다.
            encryptionKey = KDF.getInstance("HKDF-SHA256").deriveKey("AES", HKDFParameterSpec.ofExtract().addIKM(jwtSecretKey)
                    .thenExpand("playground:refresh-result:v1".getBytes(StandardCharsets.UTF_8), 32));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("인증 결과 암호화 키를 초기화하지 못했습니다.");
        }
    }

    // 로그인·갱신 DTO 모두 같은 인증 암호화를 사용하며 문맥으로 용도와 요청을 구분한다.
    public String encrypt(Object tokens, String context) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, iv, context);

            byte[] encrypted = cipher.doFinal(jsonMapper.writeValueAsBytes(tokens));

            return "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException | RuntimeException e) {
            // 직렬화 예외에는 토큰이 포함될 수 있으므로 원문·원인 예외를 응답이나 로그에 남기지 않는다.
            com.project.playground.auth.audit.LoginAuditContext.exception(e);
            com.project.playground.auth.audit.LoginAuditContext.reason("RESULT_ENCRYPTION_FAILED");
            throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
        }
    }

    // 복원할 DTO 타입은 서버 호출자가 지정하며 외부 요청에서 받지 않는다.
    public <T> T decrypt(String value, String context, Class<T> resultType) {
        try {
            if (!value.startsWith("v1.") || value.length() > 16_384)
                throw new IllegalArgumentException();

            byte[] bytes = Base64.getUrlDecoder().decode(value.substring(3));

            if (bytes.length <= IV_BYTES + 16)
                throw new IllegalArgumentException();

            byte[] iv = new byte[IV_BYTES];
            ByteBuffer.wrap(bytes).get(iv);

            byte[] plain = cipher(Cipher.DECRYPT_MODE, iv, context)
                    .doFinal(bytes, IV_BYTES, bytes.length - IV_BYTES);

            T tokens = jsonMapper.readValue(plain, resultType);

            if (tokens == null)
                throw new IllegalArgumentException();

            return tokens;
        } catch (GeneralSecurityException | RuntimeException e) {
            com.project.playground.auth.audit.LoginAuditContext.exception(e);
            com.project.playground.auth.audit.LoginAuditContext.reason("RESULT_DECRYPTION_FAILED");
            throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
        }
    }

    private Cipher cipher(int mode, byte[] iv, String context) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, encryptionKey, new GCMParameterSpec(128, iv));

        // 암호문을 다른 세션·Refresh Token·로그인 또는 갱신 식별자에 옮겨 사용할 수 없게 결합한다.
        cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));

        return cipher;
    }
}

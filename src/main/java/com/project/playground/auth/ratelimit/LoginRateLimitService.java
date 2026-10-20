package com.project.playground.auth.ratelimit;

import com.project.playground.auth.repository.RedisLoginRateLimitRepository;
import com.project.playground.auth.dto.internal.LoginRateLimitAttempt;
import com.project.playground.domain.user.repository.reader.UserReader;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * 인증 전에 계정별·IP별 로그인 시도 제한을 적용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginRateLimitService {

    private final RedisLoginRateLimitRepository rateLimitRepository;

    private final LoginRateLimitProperties properties;

    private final UserReader userReader;

    /**
     * IP 제한을 먼저 확인한 뒤 계정별 인증 시도 횟수를 증가시킨다.
     * 로그인 성공 시 초기화할 계정 카운터와 해당 요청의 IP 예약 식별자를 반환한다.
     */
    public LoginRateLimitAttempt beforeLogin(String username, String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            throw new IllegalStateException("클라이언트 접속 주소를 확인하지 못했습니다.");
        }

        try {
            // IP 제한을 먼저 적용하여 차단된 IP의 DB 조회와 비밀번호 검증을 막는다.
            String ipSubject = "ip:v2:" + fingerprint(clientIp);
            String reservationId = UUID.randomUUID().toString();
            long remainingMillis = rateLimitRepository.reserve(ipSubject, reservationId, properties.ipMaxAttempts(), properties.ipWindow());
            if (remainingMillis > 0) throw new LoginRateLimitException(Math.ceilDiv(remainingMillis, 1_000L));

            /*
             * DB가 동일한 계정으로 판단하는 입력은 같은 사용자 ID로 제한한다.
             * 존재하지 않는 아이디도 별도 카운터를 적용하여 반복 요청을 제한한다.
             * 정규화는 미등록 아이디의 제한 식별자에만 적용하며 인증 입력은 변경하지 않는다.
             */
            String accountSubject = userReader.findByUsername(username)
                    .map(user -> "account:" + user.getId())
                    .orElseGet(() -> "unknown:" + fingerprint(Normalizer.normalize(username, Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT)));

            acquire(
                    accountSubject,
                    properties.accountMaxAttempts(),
                    properties.accountWindow()
            );

            return LoginRateLimitAttempt.builder().accountSubject(accountSubject)
                    .ipSubject(ipSubject).reservationId(reservationId).build();
        } catch (DataAccessException e) {
            // 제한 상태를 확인할 수 없으면 인증을 진행하지 않고 일시적인 서비스 오류로 처리한다.
            log.error("로그인 시도 제한 확인 중 데이터 저장소 접근 실패", e);

            throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 비밀번호 인증 성공 후 계정 카운터를 초기화하고 이 요청의 IP 예약만 반환한다.
     */
    public void onAuthenticationSuccess(LoginRateLimitAttempt attempt) {
        try {
            rateLimitRepository.reset(attempt.getAccountSubject());
            // IP 예산의 다른 실패나 새 시간 창을 지우지 않는다. 성공은 공용 IP의 제한을 소모하지 않는다.
            rateLimitRepository.release(attempt.getIpSubject(), attempt.getReservationId());
        } catch (DataAccessException e) {
            // 초기화에 실패하면 신규 로그인 세션을 만들기 전에 요청을 중단한다.
            log.error("로그인 성공 후 계정 시도 카운터 초기화 실패", e);

            throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
        }
    }

    // Lua가 반환한 남은 밀리초를 올림하여 Retry-After에 사용할 초로 변환한다.
    private void acquire(String subject, int maxAttempts, Duration window) {
        long remainingMillis = rateLimitRepository.acquire(subject, maxAttempts, window);

        if (remainingMillis > 0) {
            throw new LoginRateLimitException(Math.ceilDiv(remainingMillis, 1_000L));
        }
    }

    // IP와 미등록 아이디의 원문 대신 고정 길이 지문을 Redis Key에 사용한다.
    private String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}

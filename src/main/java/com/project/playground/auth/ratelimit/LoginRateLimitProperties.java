package com.project.playground.auth.ratelimit;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 계정별·IP별 로그인 시도 제한 정책을 설정 파일에서 읽는다.
 * 잘못된 설정은 애플리케이션 시작 시 거부한다.
 */
@Validated
@ConfigurationProperties(prefix = "auth.login-rate-limit")
public record LoginRateLimitProperties(

        // 계정별 허용 인증 시도 횟수. 로그인 성공 시 해당 계정 카운터를 초기화한다.
        @Min(1)
        int accountMaxAttempts,

        // 계정별 첫 인증 시도부터 계산하는 제한 시간
        @NotNull
        @DurationMin(seconds = 1)
        Duration accountWindow,

        // IP별 허용 실패·진행 중 예약 수. 로그인 성공 시 해당 요청의 예약만 반환한다.
        @Min(1)
        int ipMaxAttempts,

        // IP별 첫 인증 시도부터 계산하는 제한 시간
        @NotNull
        @DurationMin(seconds = 1)
        Duration ipWindow
) {
}

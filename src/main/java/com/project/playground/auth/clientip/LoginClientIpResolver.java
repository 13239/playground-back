package com.project.playground.auth.clientip;

import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 실제 연결 상대가 명시적으로 등록된 BFF일 때만 단일 X-Real-IP를 로그인 제한에 사용한다.
 * 신뢰 목록이 비어 있는 개발 환경에서는 연결 IP를 사용하며 전달 헤더는 무시한다.
 */
@Component
public class LoginClientIpResolver {

    private static final String CLIENT_IP_HEADER = "X-Real-IP";

    private static final Pattern IP_LITERAL = Pattern.compile("[0-9A-Fa-f:.]{1,45}");

    private final Set<String> trustedProxyAddresses;

    private final boolean required;

    public LoginClientIpResolver(@Value("${auth.client-ip.trusted-proxies:}") String trustedProxies, Environment environment) {
        // 운영에서는 BFF 연결 주소를 사용자 IP로 대체하지 않고 누락된 배포 설정을 시작 시 거절한다.
        required = environment.matchesProfiles("prod");
        // 빈 기본값은 아무도 신뢰하지 않는다. 설정 변환기와 관계없이 쉼표 목록을 동일하게 해석한다.
        if (trustedProxies.isBlank()) {
            if (required) throw new IllegalArgumentException("운영에는 실제 BFF 주소의 신뢰 프록시 설정이 필요합니다.");
            trustedProxyAddresses = Set.of();

            return;
        }

        // 신뢰 주소도 시작 시 검증·정규화한다. 호스트명·CIDR·잘못된 설정은 허용하지 않는다.
        trustedProxyAddresses = Arrays.stream(trustedProxies.split(",", -1))
                .map(String::trim)
                .map(LoginClientIpResolver::normalizeIp)
                .collect(Collectors.toUnmodifiableSet());
    }

    public String resolve(HttpServletRequest request) {
        String remoteIp = normalizeIp(request.getRemoteAddr());

        // 외부 요청이 보낸 Forwarded·X-Forwarded-For 등으로 연결 상대의 신뢰 여부를 판단하지 않는다.
        if (!trustedProxyAddresses.contains(remoteIp)) {
            if (required) throw new BusinessException(ErrorCode.INVALID_AUTHENTICATION_REQUEST);
            return remoteIp;
        }

        var values = request.getHeaders(CLIENT_IP_HEADER);

        if (values == null || !values.hasMoreElements()) {
            // 신뢰 BFF에는 실제 사용자 IP 전달을 요구하여 프록시 전체가 하나의 제한 키를 공유하지 않게 한다.
            throw new BusinessException(ErrorCode.INVALID_AUTHENTICATION_REQUEST);
        }

        String value = values.nextElement();

        if (values.hasMoreElements()) {
            throw new BusinessException(ErrorCode.INVALID_AUTHENTICATION_REQUEST);
        }

        try {
            // 중복 헤더·쉼표 목록·포트·zone ID를 거부하고 IPv6의 표기 차이를 같은 제한 키로 통일한다.
            return normalizeIp(value.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_AUTHENTICATION_REQUEST);
        }
    }

    private static String normalizeIp(String literal) {
        if (literal == null || !IP_LITERAL.matcher(literal).matches()) {
            throw new IllegalArgumentException("단일 IP 주소만 허용합니다.");
        }

        // IPv4 축약 표기는 거부하며 Java의 리터럴 전용 API로 DNS 조회 없이 주소를 해석한다.
        if (literal.indexOf(':') < 0 && literal.chars().filter(c -> c == '.').count() != 3) {
            throw new IllegalArgumentException("IPv4 주소는 네 부분으로 입력해야 합니다.");
        }

        return InetAddress.ofLiteral(literal).getHostAddress();
    }
}

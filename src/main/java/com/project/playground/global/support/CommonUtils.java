package com.project.playground.global.support;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.lang.reflect.Array;
import java.util.*;

public final class CommonUtils {

    private CommonUtils() {
    }

    private static final String IPV4_LOCALHOST = "127.0.0.1";

    private static final String IPV6_LOCALHOST = "0:0:0:0:0:0:0:1";

    private static final String IPV6_LOCALHOST_SHORT = "::1";

    private static final Set<String> allowedIps = Set.of(IPV4_LOCALHOST, IPV6_LOCALHOST, IPV6_LOCALHOST_SHORT);

    private static final String IPV4_PATTERN = "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-4]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-4]|25[0-5])$";

    private static final String IPV6_PATTERN = "^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9]))$";

    public static boolean isInvalidIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return true;
        }

        if (allowedIps.contains(ip)) {
            return false;
        }

        return !ip.matches(IPV4_PATTERN) && !ip.matches(IPV6_PATTERN);
    }

    public static String getClientIp(HttpServletRequest request) {
        List<String> headerNames = List.of(
                "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR", "X-Cluster-Client-IP", "X-Forwarded"
        );

        String ip = headerNames.stream()
                .map(request::getHeader)
                .filter(h -> StringUtils.hasText(h) && !"unknown".equalsIgnoreCase(h))
                .findFirst()
                .orElseGet(request::getRemoteAddr);

        // 여러 IP가 있을 경우 첫 번째 IP 추출
        if (StringUtils.hasText(ip) && ip.contains(",")) {
            return ip.split(",")[0].trim();
        }

        return ip;
    }

    public static String getCookieValue(HttpServletRequest request, String name) {
        if (Objects.isNull(request.getCookies()))
            return null;

        return Arrays.stream(request.getCookies())
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    public static String toNumericString(String s) {
        return StringUtils.hasText(s) ? s.replaceAll("\\D", "") : null;
    }

    public static String trim(String s) {
        return StringUtils.hasText(s) ? s.strip() : null;
    }

    /**
     * 파라미터 중 하나라도 비어있으면 false,
     * 모두 값이 있으면 true 반환.
     */
    public static boolean isAllPresent(Object... objects) {
        if (objects == null || objects.length == 0) {
            return false;
        }

        for (Object object : objects) {
            if (!hasValue(object)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 파라미터 중 단 하나라도 정상적인 값이 존재하면 true 반환
     */
    public static boolean isAnyPresent(Object... objects) {
        if (objects == null) {
            return false;
        }

        for (Object object : objects) {
            if (hasValue(object)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 하나라도 비어있으면 true 반환
     */
    public static boolean isAnyEmpty(Object... objects) {
        return !isAllPresent(objects);
    }

    /**
     * 모든 파라미터가 비어있으면 true 반환
     */
    public static boolean isAllEmpty(Object... objects) {
        return !isAnyPresent(objects);
    }

    /**
     * 전부 다 비어있거나, 전부 다 채워져 있어야 true 반환
     */
    public static boolean isAllOrNothing(Object... objects) {
        return isAllEmpty(objects) || isAllPresent(objects);
    }

    private static boolean hasValue(Object value) {
        return switch (value) {
            case null -> false;
            case CharSequence sequence -> StringUtils.hasText(sequence);
            case Optional<?> optional -> optional.isPresent() && hasValue(optional.get());
            case Collection<?> collection -> collection.stream().anyMatch(CommonUtils::hasValue);
            case Map<?, ?> map -> map.values().stream().anyMatch(CommonUtils::hasValue);
            default -> {
                if (value.getClass().isArray()) {
                    yield hasArrayValue(value);
                }

                yield true;
            }
        };
    }

    private static boolean hasArrayValue(Object array) {
        int length = Array.getLength(array);

        if (length == 0) {
            return false;
        }

        for (int i = 0; i < length; i++) {
            if (hasValue(Array.get(array, i))) {
                return true;
            }
        }

        return false;
    }
}

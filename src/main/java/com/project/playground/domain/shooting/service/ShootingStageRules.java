package com.project.playground.domain.shooting.service;

import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import java.util.List;

/** 버전 3 별 기준. 게임 서버 공유 규칙과 함께 검증하고 밸런스 변경 시 버전을 분리한다. */
public final class ShootingStageRules {
    public static final int VERSION = 3;
    public static final List<String> MAPS = List.of("CAVE", "FACTORY", "GARDEN");
    private ShootingStageRules() {}

    public static String requireMap(String value) {
        if (value == null || !MAPS.contains(value))
            throw new BusinessException(ErrorCode.INVALID_REQUEST_BODY);
        return value;
    }

    public static int stars(String map, long millis) {
        long three = switch (requireMap(map)) {
            case "CAVE" -> 360_000L;
            case "FACTORY" -> 400_000L;
            default -> 440_000L;
        };
        long two = switch (map) {
            case "CAVE" -> 480_000L;
            case "FACTORY" -> 540_000L;
            default -> 600_000L;
        };
        return millis <= three ? 3 : millis <= two ? 2 : 1;
    }
}

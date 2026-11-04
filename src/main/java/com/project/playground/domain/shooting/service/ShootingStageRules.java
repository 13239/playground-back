package com.project.playground.domain.shooting.service;

import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import java.util.List;

/** 버전 5 패턴 공략 전투. 과거 별·해금은 보존하고 랭킹·최고 시간은 현재 난이도로 분리한다. */
public final class ShootingStageRules {
    public static final int VERSION = 5;
    public static final List<Integer> COMPATIBLE_VERSIONS = List.of(3, 4, VERSION);
    public static final List<Integer> RANKING_VERSIONS = List.of(VERSION);
    public static final String MAP_PATTERN = "CAVE|FACTORY|GARDEN|DESERT|ICE|VOLCANO";
    public static final List<String> MAPS = List.of("CAVE", "FACTORY", "GARDEN", "DESERT", "ICE", "VOLCANO");
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
            case "GARDEN" -> 440_000L;
            case "DESERT" -> 480_000L;
            case "ICE" -> 520_000L;
            default -> 560_000L;
        };
        long two = switch (map) {
            case "CAVE" -> 480_000L;
            case "FACTORY" -> 540_000L;
            case "GARDEN" -> 600_000L;
            case "DESERT" -> 660_000L;
            case "ICE" -> 720_000L;
            default -> 780_000L;
        };
        return millis <= three ? 3 : millis <= two ? 2 : 1;
    }
}

package com.project.playground.domain.adminlog.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

/** 종류·결과·대상 필터는 서버 사전에서 받아 화면과 서버의 코드 의미를 일치시킨다. */
@Getter @Builder
public class LogOptions {
    private final List<Option> types;
    private final List<Option> results;
    private final List<Option> targets;

    @Getter @Builder
    public static class Option {
        private final String code;
        private final String label;
    }
}

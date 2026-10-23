package com.project.playground.domain.game.repository;

import com.project.playground.global.exception.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 커서는 공개 정렬 값일 뿐 권한 증명이 아니다. 숫자 형식·범위를 검증해 조회에 바인딩한다. */
@Getter @AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CatalogCursor {
    private final int order;
    private final long id;
    public static CatalogCursor parse(String value) {
        if (value == null) return null;
        if (!value.matches("\\d{1,7}:\\d{1,19}")) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        try {
            String[] parts = value.split(":");
            int order = Integer.parseInt(parts[0]); long id = Long.parseLong(parts[1]);
            if (order > 1000000 || id < 1) throw new NumberFormatException();
            return new CatalogCursor(order, id);
        } catch (NumberFormatException exception) { throw new BusinessException(ErrorCode.VALIDATION_FAILED); }
    }
    public static String encode(int order, long id) { return order + ":" + id; }
}

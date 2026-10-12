package com.project.playground.global.support;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        Long totalElement,
        Long offset,
        Integer totalPage,
        Boolean isFirst,
        Boolean isLast,
        Integer pageNumber
) {

    public PageResponse(Page<T> page) {
        this(
                page.getContent(),
                page.getTotalElements(),
                page.getPageable().getOffset(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.getNumber()
        );
    }
}

package com.project.playground.domain.game.entity;

import com.project.playground.domain.game.enums.CatalogOrderType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 페이지 밖의 순서 변경과 신규 등록도 감지한다. 버전 증가는 이 행의 쓰기 잠금 안에서만 수행한다. */
@Entity
@Table(name = "tbl_catalog_order_state")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogOrderState {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 16, columnDefinition = "varchar(16)")
    private CatalogOrderType type;

    @Column(nullable = false)
    private long version;

    public void advance() {
        version = Math.incrementExact(version);
    }
}

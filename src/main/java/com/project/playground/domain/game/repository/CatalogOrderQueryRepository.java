package com.project.playground.domain.game.repository;

import com.project.playground.domain.game.dto.response.CatalogOrderItemResponse;
import com.project.playground.domain.game.entity.QGame;
import com.project.playground.domain.game.entity.QGameGenre;
import com.project.playground.domain.game.enums.CatalogOrderType;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** 순서 작업은 컬렉션을 로딩하지 않고 필요한 행과 이동 구간만 읽고 갱신한다. */
@Repository @RequiredArgsConstructor
public class CatalogOrderQueryRepository {
    private final JPAQueryFactory query;

    public long count(CatalogOrderType type) {
        Long count = switch (type) {
            case GAME -> query.select(QGame.game.count()).from(QGame.game).fetchOne();
            case GENRE -> query.select(QGameGenre.gameGenre.count()).from(QGameGenre.gameGenre).fetchOne();
        };
        return count == null ? 0 : count;
    }

    public int nextPosition(CatalogOrderType type) {
        Integer last = switch (type) {
            case GAME -> query.select(QGame.game.sortOrder.max()).from(QGame.game).fetchOne();
            case GENRE -> query.select(QGameGenre.gameGenre.sortOrder.max()).from(QGameGenre.gameGenre).fetchOne();
        };
        return last == null ? 0 : Math.incrementExact(last);
    }

    public List<CatalogOrderItemResponse> page(CatalogOrderType type, long offset, int size) {
        return switch (type) {
            case GAME -> {
                var g = QGame.game;
                yield query.select(g.code, g.name, g.sortOrder, g.version).from(g)
                        .orderBy(g.sortOrder.asc(), g.id.asc()).offset(offset).limit(size).fetch().stream()
                        .map(row -> CatalogOrderItemResponse.builder().code(row.get(g.code)).name(row.get(g.name))
                                .sortOrder(row.get(g.sortOrder)).version(row.get(g.version)).build()).toList();
            }
            case GENRE -> {
                var g = QGameGenre.gameGenre;
                yield query.select(g.code, g.name, g.sortOrder, g.version).from(g)
                        .orderBy(g.sortOrder.asc(), g.id.asc()).offset(offset).limit(size).fetch().stream()
                        .map(row -> CatalogOrderItemResponse.builder().code(row.get(g.code)).name(row.get(g.name))
                                .sortOrder(row.get(g.sortOrder)).version(row.get(g.version)).build()).toList();
            }
        };
    }

    public Optional<Integer> position(CatalogOrderType type, String code) {
        return Optional.ofNullable(switch (type) {
            case GAME -> query.select(QGame.game.sortOrder).from(QGame.game).where(QGame.game.code.eq(code)).fetchOne();
            case GENRE -> query.select(QGameGenre.gameGenre.sortOrder).from(QGameGenre.gameGenre).where(QGameGenre.gameGenre.code.eq(code)).fetchOne();
        });
    }

    public Map<String, Integer> positions(CatalogOrderType type, Collection<String> codes) {
        return switch (type) {
            case GAME -> {
                var g = QGame.game;
                yield query.select(g.code, g.sortOrder).from(g).where(g.code.in(codes)).fetch().stream()
                        .collect(Collectors.toMap(row -> row.get(g.code), row -> row.get(g.sortOrder)));
            }
            case GENRE -> {
                var g = QGameGenre.gameGenre;
                yield query.select(g.code, g.sortOrder).from(g).where(g.code.in(codes)).fetch().stream()
                        .collect(Collectors.toMap(row -> row.get(g.code), row -> row.get(g.sortOrder)));
            }
        };
    }

    /** 호출자는 목록 상태를 먼저 잠근다. 범위 갱신도 행 버전과 수정 시각을 올려 오래된 상세 저장을 거절한다. */
    public void move(CatalogOrderType type, String code, int source, int target) {
        int delta = target < source ? 1 : -1;
        var now = Instant.now();
        switch (type) {
            case GAME -> {
                var g = QGame.game;
                var range = target < source ? g.sortOrder.goe(target).and(g.sortOrder.lt(source))
                        : g.sortOrder.gt(source).and(g.sortOrder.loe(target));
                query.update(g).set(g.sortOrder, g.sortOrder.add(delta)).set(g.version, g.version.add(1))
                        .set(g.updatedAt, now).where(range).execute();
                query.update(g).set(g.sortOrder, target).set(g.version, g.version.add(1))
                        .set(g.updatedAt, now).where(g.code.eq(code)).execute();
            }
            case GENRE -> {
                var g = QGameGenre.gameGenre;
                var range = target < source ? g.sortOrder.goe(target).and(g.sortOrder.lt(source))
                        : g.sortOrder.gt(source).and(g.sortOrder.loe(target));
                query.update(g).set(g.sortOrder, g.sortOrder.add(delta)).set(g.version, g.version.add(1))
                        .set(g.updatedAt, now).where(range).execute();
                query.update(g).set(g.sortOrder, target).set(g.version, g.version.add(1))
                        .set(g.updatedAt, now).where(g.code.eq(code)).execute();
            }
        }
    }
}

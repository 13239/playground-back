package com.project.playground.domain.adminchat;

import com.project.playground.domain.adminchat.dto.AdminChatGame;
import com.project.playground.domain.game.entity.QGame;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

/** 숨김·중단 게임의 과거 채팅도 찾을 수 있도록 공개 상태로 카탈로그를 제한하지 않는다. */
@Repository @RequiredArgsConstructor @Transactional(readOnly = true)
public class AdminChatCatalog {
    private final JPAQueryFactory query;
    public List<AdminChatGame> games() {
        var game = QGame.game;
        return query.select(game.code, game.name).from(game).orderBy(game.sortOrder.asc(), game.id.asc()).fetch().stream()
                .map(row -> AdminChatGame.builder().code(row.get(game.code)).name(row.get(game.name)).build()).toList();
    }
    public Map<String, String> names(Set<String> codes) {
        if (codes.isEmpty()) return Map.of();
        var game = QGame.game;
        return query.select(game.code, game.name).from(game).where(game.code.in(codes)).fetch().stream()
                .collect(Collectors.toMap(row -> row.get(game.code), row -> row.get(game.name)));
    }
}

package com.project.playground.domain.game.repository;

import com.project.playground.domain.game.dto.internal.CatalogQuery;
import com.project.playground.domain.game.entity.*;
import com.project.playground.domain.game.module.GameModuleRegistry;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/** 페이지의 게임만 먼저 읽고 컬렉션 fetch join으로 메모리 페이징하지 않는다. */
@Repository @RequiredArgsConstructor
public class GameCatalogQueryRepository {
    private final JPAQueryFactory query;
    private final GameModuleRegistry modules;

    public List<Game> games(CatalogQuery request, boolean publicOnly) {
        QGame g = QGame.game;
        QGameMode m = QGameMode.gameMode;
        var where = new BooleanBuilder();
        String search = escapedSearch(request.getQuery());
        if (search != null) where.and(g.name.lower().like(search, '!').or(g.summary.lower().like(search, '!')));
        var cursor = CatalogCursor.parse(request.getCursor());
        if (cursor != null) where.and(g.sortOrder.gt(cursor.getOrder()).or(g.sortOrder.eq(cursor.getOrder()).and(g.id.gt(cursor.getId()))));
        if (request.getGenre() != null) {
            QGame linked = new QGame("genreGame"); QGameGenre genre = QGameGenre.gameGenre;
            var genres = new BooleanBuilder(genre.code.eq(request.getGenre()));
            if (publicOnly) genres.and(genre.enabled.isTrue());
            where.and(JPAExpressions.selectOne().from(linked).join(linked.genres, genre).where(linked.id.eq(g.id), genres).exists());
        }
        if (request.isNewOnly()) where.and(g.firstPublishedAt.goe(Instant.now().minus(30, ChronoUnit.DAYS)));
        var mode = new BooleanBuilder(m.gameId.eq(g.id));
        if (publicOnly) { where.and(g.published.isTrue()); mode.and(m.published.isTrue()).and(supported(g, m)); }
        if (request.getGraphics() != null) mode.and(m.graphicsType.eq(request.getGraphics()));
        if (request.getHumanPlayers() != null) mode.and(m.minHumanPlayers.loe(request.getHumanPlayers())).and(m.maxHumanPlayers.goe(request.getHumanPlayers()));
        if (request.getPlayers() != null) switch (request.getPlayers()) {
            case SINGLE -> mode.and(m.minHumanPlayers.loe(1)).and(m.maxHumanPlayers.goe(1));
            case TWO -> mode.and(m.minHumanPlayers.loe(2)).and(m.maxHumanPlayers.goe(2));
            case GROUP -> mode.and(m.maxHumanPlayers.goe(3));
            case MULTIPLAYER -> mode.and(m.maxHumanPlayers.goe(2));
        }
        if (publicOnly || request.getGraphics() != null || request.getPlayers() != null || request.getHumanPlayers() != null) {
            where.and(JPAExpressions.selectOne().from(m).where(mode).exists());
        }
        return query.selectFrom(g).where(where).orderBy(g.sortOrder.asc(), g.id.asc()).limit(Math.clamp(request.getLimit(), 1, 100) + 1L).fetch();
    }

    /** 잘못된 DB 선언으로 지원하지 않는 그래픽·인원을 공개 검색에 노출하지 않는다. */
    private BooleanBuilder supported(QGame game, QGameMode mode) {
        var supported = new BooleanBuilder();
        modules.all().forEach(module -> module.modes().forEach(definition -> supported.or(game.code.eq(module.code())
                .and(mode.code.eq(definition.getCode())).and(mode.definitionVersion.eq(definition.getVersion()))
                .and(mode.graphicsType.eq(definition.getGraphicsType())).and(mode.connectionType.eq(definition.getConnectionType()))
                .and(mode.interactionType.eq(definition.getInteractionType())).and(mode.minHumanPlayers.goe(definition.getMinHumanPlayers()))
                .and(mode.maxHumanPlayers.loe(definition.getMaxHumanPlayers())))));
        if (!supported.hasValue()) supported.and(game.id.isNull());
        return supported;
    }

    public List<GameGenre> genres(String searchText, String after, int limit, boolean publicOnly) {
        QGameGenre genre = QGameGenre.gameGenre;
        var where = new BooleanBuilder();
        if (publicOnly) where.and(genre.enabled.isTrue());
        String search = escapedSearch(searchText);
        if (search != null) where.and(genre.name.lower().like(search, '!').or(genre.code.lower().like(search, '!')));
        var cursor = CatalogCursor.parse(after);
        if (cursor != null) where.and(genre.sortOrder.gt(cursor.getOrder()).or(genre.sortOrder.eq(cursor.getOrder()).and(genre.id.gt(cursor.getId()))));
        return query.selectFrom(genre).where(where).orderBy(genre.sortOrder.asc(), genre.id.asc()).limit(Math.clamp(limit, 1, 100) + 1L).fetch();
    }
    private static String escapedSearch(String value) {
        return value == null || value.isBlank() ? null : "%" + value.strip().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}

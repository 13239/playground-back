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
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/** 페이지의 게임만 먼저 읽고 컬렉션 fetch join으로 메모리 페이징하지 않는다. */
@Repository @RequiredArgsConstructor
public class GameCatalogQueryRepository {
    private final JPAQueryFactory query;
    private final GameModuleRegistry modules;

    /** 관리자 목록만 offset·count를 사용하고 공개 로비의 커서 계약은 유지한다. */
    public List<Game> adminGames(String search, long offset, int size) {
        var g = QGame.game;
        return query.selectFrom(g).where(adminGameSearch(search)).orderBy(g.sortOrder.asc(), g.id.asc())
                .offset(offset).limit(size).fetch();
    }
    public long adminGameCount(String search) {
        Long count = query.select(QGame.game.count()).from(QGame.game).where(adminGameSearch(search)).fetchOne();
        return count == null ? 0 : count;
    }
    public List<GameGenre> adminGenres(String search, long offset, int size) {
        var g = QGameGenre.gameGenre;
        return query.selectFrom(g).where(adminGenreSearch(search)).orderBy(g.sortOrder.asc(), g.id.asc())
                .offset(offset).limit(size).fetch();
    }
    public long adminGenreCount(String search) {
        Long count = query.select(QGameGenre.gameGenre.count()).from(QGameGenre.gameGenre).where(adminGenreSearch(search)).fetchOne();
        return count == null ? 0 : count;
    }
    /** 현재 페이지의 장르만 한 번에 집계해 장르 수에 비례한 개별 count 쿼리를 만들지 않는다. */
    public Map<Long, Long> genreGameCounts(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        var game = QGame.game;
        var genre = QGameGenre.gameGenre;
        return query.select(genre.id, game.id.count()).from(game).join(game.genres, genre).where(genre.id.in(ids))
                .groupBy(genre.id).fetch().stream().collect(Collectors.toMap(row -> row.get(genre.id), row -> row.get(game.id.count())));
    }
    private static BooleanBuilder adminGameSearch(String value) {
        var g = QGame.game;
        var where = new BooleanBuilder();
        String search = escapedSearch(value);
        if (search != null) {
            where.and(g.name.lower().like(search, '!'));
            // 이름과 코드 검색을 실제 계약과 맞추되 한글을 ASCII 코드 컬럼에 비교하지 않는다.
            if (value.strip().chars().allMatch(character -> character <= 0x7f)) where.or(g.code.lower().like(search, '!'));
        }
        return where;
    }
    private static BooleanBuilder adminGenreSearch(String value) {
        var g = QGameGenre.gameGenre;
        var where = new BooleanBuilder();
        String search = escapedSearch(value);
        if (search != null) {
            where.and(g.name.lower().like(search, '!'));
            if (value.strip().chars().allMatch(character -> character <= 0x7f)) where.or(g.code.lower().like(search, '!'));
        }
        return where;
    }

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
        if (search != null) {
            var matches = new BooleanBuilder(genre.name.lower().like(search, '!'));
            // ASCII 코드에는 ASCII 검색어만 비교해 한글 장르 검색의 문자셋 충돌을 피한다.
            if (searchText.strip().chars().allMatch(value -> value <= 0x7f)) matches.or(genre.code.lower().like(search, '!'));
            where.and(matches);
        }
        var cursor = CatalogCursor.parse(after);
        if (cursor != null) where.and(genre.sortOrder.gt(cursor.getOrder()).or(genre.sortOrder.eq(cursor.getOrder()).and(genre.id.gt(cursor.getId()))));
        return query.selectFrom(genre).where(where).orderBy(genre.sortOrder.asc(), genre.id.asc()).limit(Math.clamp(limit, 1, 100) + 1L).fetch();
    }
    private static String escapedSearch(String value) {
        return value == null || value.isBlank() ? null : "%" + value.strip().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}

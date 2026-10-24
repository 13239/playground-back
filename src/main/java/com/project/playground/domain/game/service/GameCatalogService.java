package com.project.playground.domain.game.service;

import com.project.playground.domain.game.dto.internal.CatalogQuery;
import com.project.playground.domain.game.dto.response.*;
import com.project.playground.domain.game.entity.*;
import com.project.playground.domain.game.module.GameModuleRegistry;
import com.project.playground.domain.game.repository.*;
import com.project.playground.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

/** 공개·관리 조회는 같은 DTO 조립을 사용하되 비공개 정보는 공개 경계에서 제거한다. */
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class GameCatalogService {
    private final GameRepository games;
    private final GameModeRepository modes;
    private final GameCatalogQueryRepository queries;
    private final GameModuleRegistry modules;

    public GamePageResponse list(CatalogQuery query, boolean publicOnly) {
        var found = queries.games(query, publicOnly);
        int limit = Math.clamp(query.getLimit(), 1, 100);
        boolean hasMore = found.size() > limit;
        var page = found.stream().limit(limit).toList();
        var grouped = page.isEmpty() ? Map.<Long, List<GameMode>>of() : modes.findByGameIdInOrderBySortOrderAscIdAsc(page.stream().map(Game::getId).toList())
                .stream().collect(Collectors.groupingBy(GameMode::getGameId));
        return GamePageResponse.builder().items(page.stream().map(game -> response(game, grouped.getOrDefault(game.getId(), List.of()), publicOnly)).toList())
                .nextCursor(hasMore ? CatalogCursor.encode(page.getLast().getSortOrder(), page.getLast().getId()) : null).build();
    }
    public GameResponse get(String code, boolean publicOnly) {
        var game = games.findByCode(code).filter(value -> !publicOnly || value.isPublished())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATALOG_GAME_NOT_FOUND));
        if (publicOnly) modules.require(code);
        return response(game, modes.findByGameIdInOrderBySortOrderAscIdAsc(List.of(game.getId())), publicOnly);
    }
    public GameGenrePageResponse genres(String query, String cursor, int limit, boolean publicOnly) {
        // HTTP 외의 내부 호출도 페이지 범위를 지켜 잘못된 커서 인덱스나 무제한 조회를 만들지 않는다.
        limit = Math.clamp(limit, 1, 100);
        var found = queries.genres(query, cursor, limit, publicOnly);
        var items = found.stream().limit(limit).map(GameGenreResponse::from).toList();
        var last = found.size() > limit ? found.get(limit - 1) : null;
        return GameGenrePageResponse.builder().items(items).nextCursor(last == null ? null : CatalogCursor.encode(last.getSortOrder(), last.getId())).build();
    }
    private GameResponse response(Game game, List<GameMode> found, boolean publicOnly) {
        var genres = game.getGenres().stream().filter(genre -> !publicOnly || genre.isEnabled())
                .sorted(Comparator.comparingInt(GameGenre::getSortOrder).thenComparing(GameGenre::getId)).map(GameGenreResponse::from).toList();
        var modeResponses = found.stream().filter(mode -> !publicOnly || mode.isPublished() && modules.supports(game.getCode(), mode))
                .map(mode -> GameModeResponse.from(mode, modules.supports(game.getCode(), mode))).toList();
        return GameResponse.from(game, genres, modeResponses);
    }
}

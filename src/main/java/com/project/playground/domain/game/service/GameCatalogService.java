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
    private final GameFilterRepository filters;

    public GamePageResponse list(CatalogQuery query, boolean publicOnly) {
        var found = queries.games(query, publicOnly);
        int limit = Math.clamp(query.getLimit(), 1, 100);
        boolean hasMore = found.size() > limit;
        var page = found.stream().limit(limit).toList();
        return GamePageResponse.builder().items(responses(page, publicOnly))
                .nextCursor(hasMore ? CatalogCursor.encode(page.getLast().getSortOrder(), page.getLast().getId()) : null).build();
    }
    /** 공개 커서와 관리자 번호 페이지 모두 현재 페이지의 모드를 일괄 조회한다. */
    public List<GameResponse> responses(List<Game> page, boolean publicOnly) {
        var grouped = page.isEmpty() ? Map.<Long, List<GameMode>>of() : modes.findByGameIdInOrderByIdAsc(page.stream().map(Game::getId).toList())
                .stream().collect(Collectors.groupingBy(GameMode::getGameId));
        var selected=selectedFilters(page);
        return page.stream().map(game -> response(game, grouped.getOrDefault(game.getId(), List.of()), publicOnly,selected)).toList();
    }
    public GameResponse get(String code, boolean publicOnly) {
        var game = games.findByCode(code).filter(value -> !publicOnly || value.isPublished())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATALOG_GAME_NOT_FOUND));
        if (publicOnly) modules.require(code);
        return response(game, modes.findByGameIdInOrderByIdAsc(List.of(game.getId())), publicOnly,selectedFilters(List.of(game)));
    }
    public GameGenrePageResponse genres(String query, String cursor, int limit, boolean publicOnly) {
        // HTTP 외의 내부 호출도 페이지 범위를 지켜 잘못된 커서 인덱스나 무제한 조회를 만들지 않는다.
        limit = Math.clamp(limit, 1, 100);
        var found = queries.genres(query, cursor, limit, publicOnly);
        var items = found.stream().limit(limit).map(GameGenreResponse::from).toList();
        var last = found.size() > limit ? found.get(limit - 1) : null;
        return GameGenrePageResponse.builder().items(items).nextCursor(last == null ? null : CatalogCursor.encode(last.getSortOrder(), last.getId())).build();
    }
    private GameResponse response(Game game, List<GameMode> found, boolean publicOnly, Map<String,GameFilter> selected) {
        var genres = game.getGenres().stream().filter(genre -> !publicOnly || genre.isEnabled())
                .sorted(Comparator.comparingInt(GameFilter::getSortOrder).thenComparing(GameFilter::getId)).map(GameGenreResponse::from).toList();
        // 등록 시점이나 과거 수동 정렬값에 관계없이 배포 구현에서 정의한 모드 순서를 유지한다.
        var modeResponses = modules.orderedModes(game.getCode(), found).stream().filter(mode -> !publicOnly || mode.isPublished() && modules.supports(game.getCode(), mode))
                .map(mode -> GameModeResponse.from(mode, modules.supports(game.getCode(), mode))).toList();
        // 카드 표시와 홈 검색 모두 명시적 선택·사용 상태·현재 지원 정보를 함께 적용한다.
        var cardLabels=game.getCardLabelCodes().stream().map(selected::get).filter(Objects::nonNull)
                .filter(f -> !publicOnly || f.isEnabled() && (f.getType()==com.project.playground.domain.game.enums.GameFilterType.GENRE
                        ? genres.stream().anyMatch(g -> g.getCode().equals(f.getCode()))
                        : modeResponses.stream().anyMatch(m -> f.supportsMode(m.getGraphicsType(),m.getMinHumanPlayers(),m.getMaxHumanPlayers()))))
                .sorted(Comparator.comparingInt(GameFilter::getSortOrder)).map(f -> GameFilterResponse.from(f,0)).toList();
        var selectedLabels=publicOnly ? cardLabels.stream().map(GameFilterResponse::getLabelCode).toList() : game.getCardLabelCodes().stream().sorted().toList();
        return GameResponse.from(game,genres,modeResponses,selectedLabels,cardLabels);
    }
    /** 페이지 전체의 선택 필터를 한 번에 읽어 게임별 추가 쿼리를 만들지 않는다. */
    private Map<String,GameFilter> selectedFilters(List<Game> page) {
        var codes=page.stream().flatMap(g -> g.getCardLabelCodes().stream()).map(label -> label.substring(label.indexOf(':')+1)).distinct().toList();
        return codes.isEmpty() ? Map.of() : filters.findByCodeIn(codes).stream().collect(Collectors.toMap(GameFilter::getLabelCode,f -> f));
    }

}

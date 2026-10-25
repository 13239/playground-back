package com.project.playground.domain.game.service;

import com.project.playground.domain.authorization.enums.PermissionCode;
import com.project.playground.domain.authorization.service.AdminAuthorizationService;
import com.project.playground.domain.game.dto.response.*;
import com.project.playground.domain.game.entity.GameGenre;
import com.project.playground.domain.game.enums.CatalogOrderType;
import com.project.playground.domain.game.repository.*;
import com.project.playground.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;

/** 번호 페이지의 count·행·순서 버전을 같은 읽기 트랜잭션에서 조립한다. */
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class AdminCatalogQueryService {
    private static final Set<Integer> PAGE_SIZES = Set.of(10, 20, 30, 50);
    private final CatalogOrderStateRepository states;
    private final AdminAuthorizationService authorization;
    private final GameCatalogQueryRepository queries;
    private final GameGenreRepository genres;
    private final GameCatalogService catalog;

    /** 선택기는 관리 그리드와 같은 정렬을 사용하되 현재 필요한 옵션 페이지만 조회한다. */
    public GenreOptionPageResponse genreOptions(Long actor, String search, String cursor, int limit) {
        authorization.require(actor, PermissionCode.GAME_READ);
        int bounded = Math.clamp(limit, 1, 100);
        var found = queries.genres(search, cursor, bounded, false);
        var items = found.stream().limit(bounded).map(GenreOptionResponse::from).toList();
        var last = found.size() > bounded ? found.get(bounded - 1) : null;
        return GenreOptionPageResponse.builder().items(items)
                .nextCursor(last == null ? null : CatalogCursor.encode(last.getSortOrder(), last.getId())).build();
    }

    public AdminCatalogPageResponse<AdminGameSummaryResponse> games(Long actor, String search, int page, int size) {
        long version = readVersion(actor, CatalogOrderType.GAME, page, size);
        long count = queries.adminGameCount(search);
        int pages = pages(count, size), current = Math.min(page, pages);
        var found = queries.adminGames(search, (long) (current - 1) * size, size);
        return AdminCatalogPageResponse.<AdminGameSummaryResponse>builder()
                .items(catalog.responses(found, false).stream().map(AdminGameSummaryResponse::from).toList())
                .page(current).size(size).totalPages(pages).totalElements(count).orderVersion(version).build();
    }
    public AdminCatalogPageResponse<AdminGenreResponse> genres(Long actor, String search, int page, int size) {
        long version = readVersion(actor, CatalogOrderType.GENRE, page, size);
        long count = queries.adminGenreCount(search);
        int pages = pages(count, size), current = Math.min(page, pages);
        var found = queries.adminGenres(search, (long) (current - 1) * size, size);
        var counts = queries.genreGameCounts(found.stream().map(GameGenre::getId).toList());
        return AdminCatalogPageResponse.<AdminGenreResponse>builder()
                .items(found.stream().map(genre -> AdminGenreResponse.from(genre, counts.getOrDefault(genre.getId(), 0L))).toList())
                .page(current).size(size).totalPages(pages).totalElements(count).orderVersion(version).build();
    }
    public AdminGenreResponse genre(Long actor, String code) {
        authorization.require(actor, PermissionCode.GAME_READ);
        var genre = genres.findByCode(code).orElseThrow(() -> new BusinessException(ErrorCode.CATALOG_GENRE_NOT_FOUND));
        long count = queries.genreGameCounts(List.of(genre.getId())).getOrDefault(genre.getId(), 0L);
        return AdminGenreResponse.from(genre, count);
    }
    private long readVersion(Long actor, CatalogOrderType type, int page, int size) {
        if (page < 1 || !PAGE_SIZES.contains(size)) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        // 반복 읽기 스냅샷보다 먼저 잠가 재정렬 도중의 행과 새 순서 버전을 섞지 않는다.
        var state = states.findForRead(type).orElseThrow(() -> new BusinessException(ErrorCode.GAME_CATALOG_UNAVAILABLE));
        authorization.require(actor, PermissionCode.GAME_READ);
        return state.getVersion();
    }
    private static int pages(long count, int size) {
        return Math.toIntExact(Math.max(1, Math.ceilDiv(count, size)));
    }
}

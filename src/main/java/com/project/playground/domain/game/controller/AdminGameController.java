package com.project.playground.domain.game.controller;

import com.project.playground.domain.authorization.enums.PermissionCode;
import com.project.playground.domain.game.dto.request.*;
import com.project.playground.domain.game.dto.response.*;
import com.project.playground.domain.game.service.GameManagementService;
import com.project.playground.domain.game.service.CatalogOrderingService;
import com.project.playground.domain.game.service.AdminCatalogQueryService;
import com.project.playground.domain.game.enums.CatalogOrderType;
import com.project.playground.global.security.admin.RequireAdminPermission;
import com.project.playground.global.security.session.Session;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

/** 표시 정보와 운영 상태의 기능 권한을 분리하며 게임/모드 물리 삭제와 방 관리는 제공하지 않는다. */
@RestController @RequestMapping("/api/admin") @RequiredArgsConstructor
public class AdminGameController {
    private final GameManagementService games;
    private final CatalogOrderingService ordering;
    private final AdminCatalogQueryService queries;

    /** 순서 편집은 검색 조건 없이 전체 목록 기준의 번호 페이지와 버전을 조회한다. */
    @GetMapping("/games/order") @RequireAdminPermission(PermissionCode.GAME_READ)
    public ResponseEntity<CatalogOrderPageResponse> gameOrder(@AuthenticationPrincipal Session session,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(10) @Max(50) int size) {
        return response(ordering.page(session.getId(), CatalogOrderType.GAME, page, size));
    }

    @PutMapping("/games/order") @RequireAdminPermission(PermissionCode.GAME_WRITE)
    public ResponseEntity<CatalogOrderResponse> reorderGames(@AuthenticationPrincipal Session session,
            @Valid @RequestBody CatalogOrderRequest request) {
        return response(ordering.reorder(session.getId(), CatalogOrderType.GAME, request));
    }

    @GetMapping("/game-genres/order") @RequireAdminPermission(PermissionCode.GAME_READ)
    public ResponseEntity<CatalogOrderPageResponse> genreOrder(@AuthenticationPrincipal Session session,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(10) @Max(50) int size) {
        return response(ordering.page(session.getId(), CatalogOrderType.GENRE, page, size));
    }

    @PutMapping("/game-genres/order") @RequireAdminPermission(PermissionCode.GAME_WRITE)
    public ResponseEntity<CatalogOrderResponse> reorderGenres(@AuthenticationPrincipal Session session,
            @Valid @RequestBody CatalogOrderRequest request) {
        return response(ordering.reorder(session.getId(), CatalogOrderType.GENRE, request));
    }

    @GetMapping("/games") @RequireAdminPermission(PermissionCode.GAME_READ)
    public ResponseEntity<AdminCatalogPageResponse<AdminGameSummaryResponse>> list(@AuthenticationPrincipal Session session,
            @RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(10) @Max(50) int size) {
        return response(queries.games(session.getId(), query, page, size));
    }
    @GetMapping("/game-implementations") @RequireAdminPermission(PermissionCode.GAME_READ)
    public ResponseEntity<List<GameImplementationResponse>> implementations(@AuthenticationPrincipal Session session) {
        return response(games.implementations(session.getId()));
    }
    @GetMapping("/games/{code}") @RequireAdminPermission(PermissionCode.GAME_READ)
    public ResponseEntity<GameResponse> get(@AuthenticationPrincipal Session session,
            @PathVariable @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String code) {
        return response(games.get(session.getId(), code));
    }
    @PostMapping("/games") @RequireAdminPermission(PermissionCode.GAME_WRITE)
    public ResponseEntity<GameResponse> create(@AuthenticationPrincipal Session session, @Valid @RequestBody CreateGameRequest request) {
        var result = games.create(session.getId(), request);
        return ResponseEntity.created(URI.create("/api/admin/games/" + result.getCode())).cacheControl(CacheControl.noStore()).body(result);
    }
    @PutMapping("/games/{code}") @RequireAdminPermission(PermissionCode.GAME_WRITE)
    public ResponseEntity<GameResponse> update(@AuthenticationPrincipal Session session,
            @PathVariable @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String code, @Valid @RequestBody UpdateGameRequest request) {
        return response(games.update(session.getId(), code, request));
    }
    @PutMapping("/games/{code}/availability") @RequireAdminPermission(PermissionCode.GAME_AVAILABILITY_UPDATE)
    public ResponseEntity<GameResponse> availability(@AuthenticationPrincipal Session session,
            @PathVariable @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String code, @Valid @RequestBody GameAvailabilityRequest request) {
        return response(games.availability(session.getId(), code, request));
    }
    @PostMapping("/games/{code}/modes/{mode}") @RequireAdminPermission(PermissionCode.GAME_WRITE)
    public ResponseEntity<GameModeResponse> createMode(@AuthenticationPrincipal Session session,
            @PathVariable @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String code,
            @PathVariable @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String mode, @Valid @RequestBody GameModeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(games.mode(session.getId(), code, mode, request, true));
    }
    @PutMapping("/games/{code}/modes/{mode}") @RequireAdminPermission(PermissionCode.GAME_WRITE)
    public ResponseEntity<GameModeResponse> updateMode(@AuthenticationPrincipal Session session,
            @PathVariable @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String code,
            @PathVariable @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String mode, @Valid @RequestBody GameModeRequest request) {
        return response(games.mode(session.getId(), code, mode, request, false));
    }
    @PutMapping("/games/{code}/modes/{mode}/availability") @RequireAdminPermission(PermissionCode.GAME_AVAILABILITY_UPDATE)
    public ResponseEntity<GameModeResponse> modeAvailability(@AuthenticationPrincipal Session session,
            @PathVariable @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String code,
            @PathVariable @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String mode, @Valid @RequestBody GameAvailabilityRequest request) {
        return response(games.modeAvailability(session.getId(), code, mode, request));
    }
    @GetMapping("/game-genres") @RequireAdminPermission(PermissionCode.GAME_READ)
    public ResponseEntity<AdminCatalogPageResponse<AdminGenreResponse>> genres(@AuthenticationPrincipal Session session,
            @RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(10) @Max(50) int size) {
        return response(queries.genres(session.getId(), query, page, size));
    }
    /** 장르 선택기의 검색·커서는 관리 그리드의 번호 페이지 계약과 분리한다. */
    @GetMapping("/game-genres/options") @RequireAdminPermission(PermissionCode.GAME_READ)
    public ResponseEntity<GenreOptionPageResponse> genreOptions(@AuthenticationPrincipal Session session,
            @RequestParam(required = false) @Size(max = 100) String query, @RequestParam(required = false) @Size(max = 27) String cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return response(queries.genreOptions(session.getId(), query, cursor, limit));
    }
    @GetMapping("/game-genres/{code}") @RequireAdminPermission(PermissionCode.GAME_READ)
    public ResponseEntity<AdminGenreResponse> genre(@AuthenticationPrincipal Session session,
            @PathVariable @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String code) {
        return response(queries.genre(session.getId(), code));
    }
    @PostMapping("/game-genres/{code}") @RequireAdminPermission(PermissionCode.GAME_WRITE)
    public ResponseEntity<GameGenreResponse> createGenre(@AuthenticationPrincipal Session session,
            @PathVariable @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String code, @Valid @RequestBody GameGenreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(games.genre(session.getId(), code, request, true));
    }
    @PutMapping("/game-genres/{code}") @RequireAdminPermission(PermissionCode.GAME_WRITE)
    public ResponseEntity<GameGenreResponse> updateGenre(@AuthenticationPrincipal Session session,
            @PathVariable @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String code, @Valid @RequestBody GameGenreRequest request) {
        return response(games.genre(session.getId(), code, request, false));
    }
    private static <T> ResponseEntity<T> response(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}

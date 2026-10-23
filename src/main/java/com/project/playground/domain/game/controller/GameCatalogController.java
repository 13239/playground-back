package com.project.playground.domain.game.controller;

import com.project.playground.domain.game.dto.internal.CatalogQuery;
import com.project.playground.domain.game.dto.response.*;
import com.project.playground.domain.game.enums.GraphicsType;
import com.project.playground.domain.game.service.*;
import com.project.playground.global.security.session.Session;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 공개 목록도 BFF 서버 인증을 유지한다. 신규 실행은 기존 세션·브라우저 증명을 추가로 요구한다. */
@RestController @RequiredArgsConstructor
public class GameCatalogController {
    private final GameCatalogService catalog;
    private final GameAvailabilityService availability;

    @GetMapping("/api/games")
    public ResponseEntity<GamePageResponse> list(@RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(required = false) @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String genre,
            @RequestParam(required = false) GraphicsType graphics, @RequestParam(required = false) CatalogQuery.Players players,
            @RequestParam(required = false) @Min(1) @Max(10000) Integer humanPlayers,
            @RequestParam(defaultValue = "false") boolean newOnly, @RequestParam(required = false) @Size(max = 27) String cursor,
            @RequestParam(defaultValue = "40") @Min(1) @Max(100) int limit) {
        return response(catalog.list(CatalogQuery.builder().query(query).genre(genre).graphics(graphics).players(players)
                .humanPlayers(humanPlayers).newOnly(newOnly).cursor(cursor).limit(limit).build(), true));
    }
    @GetMapping("/api/games/{code}")
    public ResponseEntity<GameResponse> get(@PathVariable @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String code) {
        return response(catalog.get(code, true));
    }
    @GetMapping("/api/game-genres")
    public ResponseEntity<GameGenrePageResponse> genres(@RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(required = false) @Size(max = 27) String cursor, @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return response(catalog.genres(query, cursor, limit, true));
    }
    @PostMapping("/api/games/{code}/modes/{mode}/admission")
    public ResponseEntity<GameModeResponse> admission(@AuthenticationPrincipal(errorOnInvalidType = true) Session session,
            @PathVariable @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String code,
            @PathVariable @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String mode) {
        // 이 응답은 새 실행에 대한 현재 정책 확인이며 장기 이용권이나 기존 대국 상태가 아니다.
        return response(availability.requireNewPlay(code, mode));
    }
    private static <T> ResponseEntity<T> response(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}

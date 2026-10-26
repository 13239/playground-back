package com.project.playground.domain.game.controller;

import com.project.playground.domain.authorization.enums.PermissionCode;
import com.project.playground.domain.game.dto.request.*;
import com.project.playground.domain.game.dto.response.*;
import com.project.playground.domain.game.enums.*;
import com.project.playground.domain.game.service.*;
import com.project.playground.global.security.admin.RequireAdminPermission;
import com.project.playground.global.security.session.Session;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 공개 조회는 BFF 인증을 유지하고 관리 명령은 기존 관리자 권한·본문 증명으로 보호한다. */
@RestController @RequiredArgsConstructor
public class GameFilterController {
    private final GameFilterService filters;
    private final CatalogOrderingService ordering;
    @GetMapping("/api/game-filters")
    public ResponseEntity<GameFilterPageResponse> publicFilters(@RequestParam(required=false) @Size(max=100) String query,
            @RequestParam(required=false) @Size(max=27) String cursor,@RequestParam(defaultValue="50") @Min(1) @Max(100) int limit) {
        return response(filters.options(null,query,cursor,limit,true));
    }
    @GetMapping("/api/admin/game-filters") @RequireAdminPermission(PermissionCode.GAME_READ)
    public ResponseEntity<AdminCatalogPageResponse<GameFilterResponse>> list(@AuthenticationPrincipal Session session,
            @RequestParam(required=false) @Size(max=100) String query,@RequestParam(required=false) GameFilterType type,
            @RequestParam(required=false) Boolean enabled,@RequestParam(defaultValue="1") @Min(1) int page,
            @RequestParam(defaultValue="20") @Min(20) @Max(50) int size) { return response(filters.list(session.getId(),query,type,enabled,page,size)); }
    @GetMapping("/api/admin/game-filters/options") @RequireAdminPermission(PermissionCode.GAME_READ)
    public ResponseEntity<GameFilterPageResponse> options(@AuthenticationPrincipal Session session,@RequestParam(required=false) @Size(max=100) String query,
            @RequestParam(required=false) @Size(max=27) String cursor,@RequestParam(defaultValue="100") @Min(1) @Max(100) int limit) {
        return response(filters.options(session.getId(),query,cursor,limit,false));
    }
    @GetMapping("/api/admin/game-filters/order") @RequireAdminPermission(PermissionCode.GAME_READ)
    public ResponseEntity<CatalogOrderPageResponse> order(@AuthenticationPrincipal Session session,@RequestParam(defaultValue="1") @Min(1) int page,
            @RequestParam(defaultValue="20") @Min(20) @Max(50) int size) { return response(ordering.page(session.getId(),CatalogOrderType.FILTER,page,size)); }
    @PutMapping("/api/admin/game-filters/order") @RequireAdminPermission(PermissionCode.GAME_WRITE)
    public ResponseEntity<CatalogOrderResponse> reorder(@AuthenticationPrincipal Session session,@Valid @RequestBody CatalogOrderRequest request) {
        return response(ordering.reorder(session.getId(),CatalogOrderType.FILTER,request));
    }
    @GetMapping("/api/admin/game-filters/{code}") @RequireAdminPermission(PermissionCode.GAME_READ)
    public ResponseEntity<GameFilterResponse> get(@AuthenticationPrincipal Session session,@PathVariable @Pattern(regexp="[A-Z][A-Z0-9_]{0,63}") String code) { return response(filters.get(session.getId(),code)); }
    @PostMapping("/api/admin/game-filters/{code}") @RequireAdminPermission(PermissionCode.GAME_WRITE)
    public ResponseEntity<GameFilterResponse> create(@AuthenticationPrincipal Session session,@PathVariable @Pattern(regexp="[A-Z][A-Z0-9_]{0,63}") String code,
            @Valid @RequestBody GameFilterRequest request) { return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(filters.save(session.getId(),code,request,true)); }
    @PutMapping("/api/admin/game-filters/{code}") @RequireAdminPermission(PermissionCode.GAME_WRITE)
    public ResponseEntity<GameFilterResponse> update(@AuthenticationPrincipal Session session,@PathVariable @Pattern(regexp="[A-Z][A-Z0-9_]{0,63}") String code,
            @Valid @RequestBody GameFilterRequest request) { return response(filters.save(session.getId(),code,request,false)); }
    @DeleteMapping("/api/admin/game-filters/{code}") @RequireAdminPermission(PermissionCode.GAME_WRITE)
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Session session,@PathVariable @Pattern(regexp="[A-Z][A-Z0-9_]{0,63}") String code,
            @RequestParam @PositiveOrZero long expectedVersion) { filters.delete(session.getId(),code,expectedVersion); return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build(); }
    private static <T> ResponseEntity<T> response(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}

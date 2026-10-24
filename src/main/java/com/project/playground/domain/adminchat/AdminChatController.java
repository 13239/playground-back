package com.project.playground.domain.adminchat;

import com.project.playground.domain.adminchat.dto.*;
import com.project.playground.domain.authorization.enums.PermissionCode;
import com.project.playground.global.exception.*;
import com.project.playground.global.security.admin.RequireAdminPermission;
import com.project.playground.global.security.session.Session;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import java.time.*;
import java.util.*;

/** 현재 CHAT_HISTORY_READ 권한으로만 목록·카탈로그·한 메시지를 읽는다. 쓰기 경로는 제공하지 않는다. */
@RestController @RequestMapping("/api/admin/chat") @RequiredArgsConstructor
public class AdminChatController {
    private final AdminChatService chat;
    @GetMapping("/games") @RequireAdminPermission(PermissionCode.CHAT_HISTORY_READ)
    public ResponseEntity<List<AdminChatGame>> games(@AuthenticationPrincipal Session actor) { return response(chat.games(actor.getId())); }
    @GetMapping("/messages") @RequireAdminPermission(PermissionCode.CHAT_HISTORY_READ)
    public ResponseEntity<AdminChatPage> messages(@AuthenticationPrincipal Session actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(required = false) @Pattern(regexp = "GLOBAL|GAME_ROOM") String scope,
            @RequestParam(required = false) @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String gameCode,
            @RequestParam(required = false) @Positive @Max(9_007_199_254_740_991L) Long roomId,
            @RequestParam(defaultValue = "1") @Positive int page, @RequestParam(defaultValue = "10") int size) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul")), start = from == null ? today.minusDays(6) : from, end = to == null ? today : to;
        if (start.isAfter(end) || start.getYear() < 1000 || !end.isBefore(LocalDate.of(9999, 12, 31)) || !Set.of(10, 20, 30, 50).contains(size)
                || "GLOBAL".equals(scope) && (gameCode != null || roomId != null)) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        var params = UriComponentsBuilder.fromPath("/internal/admin/chat/messages").queryParam("from", start).queryParam("to", end).queryParam("page", page).queryParam("size", size);
        // 변수 확장을 사용해 +, &, %, 한글도 검색 문자열 그대로 인코딩한다.
        if (query != null && !query.isBlank()) params.queryParam("query", "{author}");
        if (scope != null) params.queryParam("scope", scope);
        if (gameCode != null) params.queryParam("gameCode", gameCode);
        if (roomId != null) params.queryParam("roomId", roomId);
        return response(chat.search(actor.getId(), params.encode().buildAndExpand(Map.of("author", query == null ? "" : query.strip())).toUriString()));
    }
    @GetMapping("/messages/{id}") @RequireAdminPermission(PermissionCode.CHAT_HISTORY_READ)
    public ResponseEntity<AdminChatMessage> detail(@AuthenticationPrincipal Session actor, @PathVariable @Pattern(regexp = "[a-f0-9]{24}") String id) { return response(chat.detail(actor.getId(), id)); }
    private <T> ResponseEntity<T> response(T value) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value); }
}

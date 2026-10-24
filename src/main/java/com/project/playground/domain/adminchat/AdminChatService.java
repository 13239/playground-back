package com.project.playground.domain.adminchat;

import com.project.playground.domain.adminchat.dto.*;
import com.project.playground.domain.authorization.enums.PermissionCode;
import com.project.playground.domain.authorization.service.AdminAuthorizationService;
import com.project.playground.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/** 요청마다 DB 권한을 확인하되 외부 통신 동안 JDBC 트랜잭션을 점유하지 않는다. */
@Service @RequiredArgsConstructor
public class AdminChatService {
    private final AdminAuthorizationService authorization;
    private final AdminChatClient chat;
    private final AdminChatCatalog catalog;

    public List<AdminChatGame> games(Long actor) { require(actor); return catalog.games(); }
    public AdminChatPage search(Long actor, String path) {
        require(actor);
        var page = chat.get(path, AdminChatPage.class);
        // 배포 버전 차이·손상 응답은 빈 결과로 오인하지 않고 안전한 일시 장애로 처리한다.
        if (page.getItems() == null || !Set.of(10, 20, 30, 50).contains(page.getSize()) || page.getPage() < 1
                || page.getPage() > page.getTotalPages() || page.getTotalElements() < 0
                || page.getTotalPages() != Math.max(1L, (page.getTotalElements() + page.getSize() - 1) / page.getSize())
                || page.getItems().size() > page.getSize() || page.getItems().size() > page.getTotalElements()) throw unavailable();
        page.getItems().forEach(this::validate);
        var names = names(page.getItems());
        return page.toBuilder().items(page.getItems().stream().map(item -> enrich(item, names)).toList()).build();
    }
    public AdminChatMessage detail(Long actor, String id) {
        require(actor);
        var message = chat.get("/internal/admin/chat/messages/" + id, AdminChatMessage.class);
        validate(message);
        if (!id.equals(message.getId())) throw unavailable();
        return enrich(message, names(List.of(message)));
    }
    private void require(Long actor) { authorization.require(actor, PermissionCode.CHAT_HISTORY_READ); }
    private Map<String, String> names(List<AdminChatMessage> items) { return catalog.names(items.stream().map(AdminChatMessage::getGameCode).filter(Objects::nonNull).collect(Collectors.toSet())); }
    private AdminChatMessage enrich(AdminChatMessage message, Map<String, String> names) {
        return message.toBuilder().gameName(message.getGameCode() == null ? null : names.getOrDefault(message.getGameCode(), message.getGameCode())).build();
    }
    private void validate(AdminChatMessage message) {
        if (message == null || message.getId() == null || !message.getId().matches("[a-f0-9]{24}") || message.getSentAt() == null
                || message.getAuthorId() == null || message.getAuthorId() <= 0 || message.getAuthorNickname() == null || message.getAuthorNickname().isBlank()
                || message.getAuthorNickname().codePointCount(0, message.getAuthorNickname().length()) > 50
                || message.getAuthorAvatarId() == null || !message.getAuthorAvatarId().matches("[A-Z_]{2,20}")
                || message.getContent() == null || message.getContent().isBlank() || message.getContent().codePointCount(0, message.getContent().length()) > 1000) throw unavailable();
        if ("GLOBAL".equals(message.getScope())) {
            if (message.getGameCode() != null || message.getRoomId() != null) throw unavailable();
        } else if (!"GAME_ROOM".equals(message.getScope()) || message.getGameCode() == null || !message.getGameCode().matches("[A-Z][A-Z0-9_]{0,63}")
                || message.getRoomId() == null || message.getRoomId() <= 0) throw unavailable();
    }
    private BusinessException unavailable() { return new BusinessException(ErrorCode.ADMIN_CHAT_UNAVAILABLE); }
}

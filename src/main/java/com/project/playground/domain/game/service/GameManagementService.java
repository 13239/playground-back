package com.project.playground.domain.game.service;

import com.project.playground.domain.adminaudit.service.AdminAuditService;
import com.project.playground.domain.authorization.dto.response.AdminContextResponse;
import com.project.playground.domain.authorization.enums.PermissionCode;
import com.project.playground.domain.authorization.repository.RoleRepository;
import com.project.playground.domain.authorization.service.AdminAuthorizationService;
import com.project.playground.domain.game.dto.internal.CatalogQuery;
import com.project.playground.domain.game.dto.request.*;
import com.project.playground.domain.game.dto.response.*;
import com.project.playground.domain.game.entity.*;
import com.project.playground.domain.game.module.*;
import com.project.playground.domain.game.repository.*;
import com.project.playground.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** 관리 명령은 현재 권한·편집 버전·구현 지원 범위·동기 감사 기록을 같은 트랜잭션으로 처리한다. */
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class GameManagementService {
    private final AdminAuthorizationService authorization;
    private final RoleRepository roles;
    private final GameRepository games;
    private final GameModeRepository modes;
    private final GameGenreRepository genres;
    private final GameModuleRegistry modules;
    private final GameCatalogService catalog;
    private final AdminAuditService audit;

    public GamePageResponse list(Long actor, CatalogQuery query) {
        authorization.require(actor, PermissionCode.GAME_READ); return catalog.list(query, false);
    }
    public GameResponse get(Long actor, String code) {
        authorization.require(actor, PermissionCode.GAME_READ); return catalog.get(code, false);
    }
    public GameGenrePageResponse genres(Long actor, String query, String cursor, int limit) {
        authorization.require(actor, PermissionCode.GAME_READ); return catalog.genres(query, cursor, limit, false);
    }
    public List<GameImplementationResponse> implementations(Long actor) {
        authorization.require(actor, PermissionCode.GAME_READ);
        return modules.all().stream().map(GameImplementationResponse::from).toList();
    }
    @Transactional
    public GameResponse create(Long actorId, CreateGameRequest request) {
        var actor = requireWrite(actorId, PermissionCode.GAME_WRITE);
        var module = modules.require(request.getCode());
        if (games.existsByCode(request.getCode())) throw new BusinessException(ErrorCode.GAME_CATALOG_CONFLICT);
        var metadata = request.getMetadata();
        var assigned = validateMetadata(module, metadata);
        var game = games.saveAndFlush(Game.builder().code(request.getCode()).name(metadata.getName()).summary(metadata.getSummary())
                .description(metadata.getDescription()).thumbnailKey(metadata.getThumbnailKey()).sortOrder(metadata.getSortOrder()).genres(assigned).build());
        audit.write(actor, "GAME_CREATED", "GAME", game.getCode(), request.getReason(), Map.of("after", snapshot(game)));
        return catalog.get(game.getCode(), false);
    }
    @Transactional
    public GameResponse update(Long actorId, String code, UpdateGameRequest request) {
        var actor = requireWrite(actorId, PermissionCode.GAME_WRITE);
        var game = locked(code); requireVersion(game.getVersion(), request.getExpectedVersion());
        var metadata = request.getMetadata(); var assigned = validateMetadata(modules.require(code), metadata);
        var before = snapshot(game);
        game.update(metadata.getName(), metadata.getSummary(), metadata.getDescription(), metadata.getThumbnailKey(), metadata.getSortOrder(), assigned);
        games.flush();
        audit.write(actor, "GAME_UPDATED", "GAME", code, request.getReason(), Map.of("before", before, "after", snapshot(game)));
        return catalog.get(code, false);
    }
    @Transactional
    public GameResponse availability(Long actorId, String code, GameAvailabilityRequest request) {
        var actor = requireWrite(actorId, PermissionCode.GAME_AVAILABILITY_UPDATE);
        var game = locked(code); requireVersion(game.getVersion(), request.getExpectedVersion());
        modules.require(code);
        var before = snapshot(game);
        game.changeAvailability(request.getPublished(), request.getAvailability(), request.getMessage()); games.flush();
        audit.write(actor, "GAME_AVAILABILITY_UPDATED", "GAME", code, request.getReason(), Map.of("before", before, "after", snapshot(game)));
        return catalog.get(code, false);
    }
    @Transactional
    public GameModeResponse mode(Long actorId, String gameCode, String modeCode, GameModeRequest request, boolean create) {
        var actor = requireWrite(actorId, PermissionCode.GAME_WRITE);
        var game = locked(gameCode); var definition = modules.requireMode(gameCode, modeCode);
        modules.validatePlayers(definition, request.getMinHumanPlayers(), request.getMaxHumanPlayers());
        var existing = modes.findByGameIdAndCode(game.getId(), modeCode);
        if (create && existing.isPresent()) throw new BusinessException(ErrorCode.GAME_CATALOG_CONFLICT);
        var mode = create ? GameMode.builder().gameId(game.getId()).definition(definition).name(request.getName())
                .minHumanPlayers(request.getMinHumanPlayers()).maxHumanPlayers(request.getMaxHumanPlayers()).sortOrder(request.getSortOrder()).build()
                : existing.orElseThrow(() -> new BusinessException(ErrorCode.CATALOG_MODE_NOT_FOUND));
        if (create) requireVersion(0, request.getExpectedVersion()); else requireVersion(mode.getVersion(), request.getExpectedVersion());
        var before = create ? Map.<String, Object>of() : snapshot(mode);
        if (!create) mode.update(definition, request.getName(), request.getMinHumanPlayers(), request.getMaxHumanPlayers(), request.getSortOrder());
        modes.saveAndFlush(mode);
        audit.write(actor, create ? "GAME_MODE_CREATED" : "GAME_MODE_UPDATED", "GAME_MODE", gameCode + ":" + modeCode,
                request.getReason(), Map.of("before", before, "after", snapshot(mode)));
        return GameModeResponse.from(mode, modules.supports(gameCode, mode));
    }
    @Transactional
    public GameModeResponse modeAvailability(Long actorId, String gameCode, String modeCode, GameAvailabilityRequest request) {
        var actor = requireWrite(actorId, PermissionCode.GAME_AVAILABILITY_UPDATE);
        var game = locked(gameCode);
        var mode = modes.findByGameIdAndCode(game.getId(), modeCode).orElseThrow(() -> new BusinessException(ErrorCode.CATALOG_MODE_NOT_FOUND));
        requireVersion(mode.getVersion(), request.getExpectedVersion());
        if (!modules.supports(gameCode, mode)) throw new BusinessException(ErrorCode.GAME_IMPLEMENTATION_UNAVAILABLE);
        var before = snapshot(mode);
        mode.changeAvailability(request.getPublished(), request.getAvailability(), request.getMessage()); modes.flush();
        audit.write(actor, "GAME_MODE_AVAILABILITY_UPDATED", "GAME_MODE", gameCode + ":" + modeCode, request.getReason(),
                Map.of("before", before, "after", snapshot(mode)));
        return GameModeResponse.from(mode, true);
    }
    @Transactional
    public GameGenreResponse genre(Long actorId, String code, GameGenreRequest request, boolean create) {
        var actor = requireWrite(actorId, PermissionCode.GAME_WRITE);
        var existing = genres.findForUpdate(code);
        if (create && existing.isPresent()) throw new BusinessException(ErrorCode.GAME_CATALOG_CONFLICT);
        var genre = create ? GameGenre.builder().code(code).name(request.getName()).sortOrder(request.getSortOrder()).enabled(request.getEnabled()).build()
                : existing.orElseThrow(() -> new BusinessException(ErrorCode.CATALOG_GENRE_NOT_FOUND));
        if (create) requireVersion(0, request.getExpectedVersion()); else requireVersion(genre.getVersion(), request.getExpectedVersion());
        var before = create ? Map.<String, Object>of() : snapshot(genre);
        if (!create) genre.update(request.getName(), request.getEnabled(), request.getSortOrder());
        genres.saveAndFlush(genre);
        audit.write(actor, create ? "GAME_GENRE_CREATED" : "GAME_GENRE_UPDATED", "GAME_GENRE", code, request.getReason(),
                Map.of("before", before, "after", snapshot(genre)));
        return GameGenreResponse.from(genre);
    }
    private AdminContextResponse requireWrite(Long actor, PermissionCode permission) {
        // 기능 권한 편집과도 순서를 맞춘다. 게임 정책 변경은 방/대국 행을 잠그거나 종료하지 않는다.
        roles.lockAuthorizationPolicy().orElseThrow(() -> new BusinessException(ErrorCode.AUTHORIZATION_SERVICE_UNAVAILABLE));
        return authorization.require(actor, permission);
    }
    private Game locked(String code) { return games.findForUpdate(code).orElseThrow(() -> new BusinessException(ErrorCode.CATALOG_GAME_NOT_FOUND)); }
    private Set<GameGenre> validateMetadata(GameModule module, GameMetadataRequest metadata) {
        if (!module.thumbnailKeys().contains(metadata.getThumbnailKey())) throw new BusinessException(ErrorCode.GAME_CONFIGURATION_INVALID);
        var assigned = genres.findByCodeIn(metadata.getGenreCodes());
        if (assigned.size() != metadata.getGenreCodes().size()) throw new BusinessException(ErrorCode.CATALOG_GENRE_NOT_FOUND);
        return new HashSet<>(assigned);
    }
    private static void requireVersion(long actual, long expected) {
        if (actual != expected) throw new BusinessException(ErrorCode.GAME_CATALOG_CHANGED);
    }
    private static Map<String, Object> snapshot(Game game) {
        var values = new LinkedHashMap<String, Object>();
        values.put("code", game.getCode()); values.put("name", game.getName()); values.put("summary", game.getSummary());
        values.put("description", game.getDescription()); values.put("thumbnailKey", game.getThumbnailKey());
        values.put("genres", game.getGenres().stream().map(GameGenre::getCode).sorted().toList()); values.put("sortOrder", game.getSortOrder());
        values.put("published", game.isPublished()); values.put("availability", game.getAvailability().name());
        values.put("message", game.getUnavailableMessage()); values.put("version", game.getVersion()); return values;
    }
    private static Map<String, Object> snapshot(GameMode mode) {
        return Map.of("code", mode.getCode(), "name", mode.getName(), "minHumanPlayers", mode.getMinHumanPlayers(),
                "maxHumanPlayers", mode.getMaxHumanPlayers(), "definitionVersion", mode.getDefinitionVersion(), "sortOrder", mode.getSortOrder(),
                "published", mode.isPublished(), "availability", mode.getAvailability().name(), "message", mode.getUnavailableMessage(), "version", mode.getVersion());
    }
    private static Map<String, Object> snapshot(GameGenre genre) {
        return Map.of("code", genre.getCode(), "name", genre.getName(), "enabled", genre.isEnabled(), "sortOrder", genre.getSortOrder(), "version", genre.getVersion());
    }
}

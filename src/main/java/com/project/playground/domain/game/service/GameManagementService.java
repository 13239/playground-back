package com.project.playground.domain.game.service;

import com.project.playground.domain.adminaudit.service.AdminAuditService;
import com.project.playground.domain.authorization.dto.response.AdminContextResponse;
import com.project.playground.domain.authorization.enums.PermissionCode;
import com.project.playground.domain.authorization.repository.RoleRepository;
import com.project.playground.domain.authorization.service.AdminAuthorizationService;
import com.project.playground.domain.game.dto.request.*;
import com.project.playground.domain.game.dto.response.*;
import com.project.playground.domain.game.entity.*;
import com.project.playground.domain.game.enums.CatalogOrderType;
import com.project.playground.domain.game.module.*;
import com.project.playground.domain.game.repository.*;
import com.project.playground.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

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
    private final CatalogOrderingService ordering;

    public GameResponse get(Long actor, String code) {
        authorization.require(actor, PermissionCode.GAME_READ); return catalog.get(code, false);
    }
    public List<GameImplementationResponse> implementations(Long actor) {
        authorization.require(actor, PermissionCode.GAME_READ);
        var implementations = modules.all();
        var registered = implementations.isEmpty() ? Set.<String>of() : games.findRegisteredCodes(implementations.stream().map(GameModule::code).toList());
        return implementations.stream().map(module -> GameImplementationResponse.from(module, registered.contains(module.code()))).toList();
    }
    @Transactional
    public GameResponse create(Long actorId, CreateGameRequest request) {
        var actor = requireWrite(actorId, PermissionCode.GAME_WRITE);
        modules.require(request.getCode());
        if (games.existsByCode(request.getCode())) throw new BusinessException(ErrorCode.GAME_CATALOG_CONFLICT);
        var metadata = request.getMetadata();
        var assigned = validateMetadata(metadata, Set.of());
        var game = games.saveAndFlush(Game.builder().code(request.getCode()).name(metadata.getName()).summary(metadata.getSummary())
                // 신규 게임은 전체 목록 끝에 배치하고 순서 편집 버전도 같은 트랜잭션에서 갱신한다.
                .description(metadata.getDescription()).sortOrder(ordering.reserveLast(CatalogOrderType.GAME)).genres(assigned).build());
        audit.write(actor, "GAME_CREATED", "GAME", game.getCode(), null, Map.of("after", snapshot(game)));
        return catalog.get(game.getCode(), false);
    }
    @Transactional
    public GameResponse update(Long actorId, String code, UpdateGameRequest request) {
        var actor = requireWrite(actorId, PermissionCode.GAME_WRITE);
        var game = locked(code); requireVersion(game.getVersion(), request.getExpectedVersion());
        modules.require(code);
        var metadata = request.getMetadata(); var assigned = validateMetadata(metadata, game.getGenres());
        var before = snapshot(game);
        game.update(metadata.getName(), metadata.getSummary(), metadata.getDescription(), assigned);
        games.flush();
        audit.write(actor, "GAME_UPDATED", "GAME", code, null, Map.of("before", before, "after", snapshot(game)));
        return catalog.get(code, false);
    }
    @Transactional
    public GameResponse availability(Long actorId, String code, GameAvailabilityRequest request) {
        var actor = requireWrite(actorId, PermissionCode.GAME_AVAILABILITY_UPDATE);
        var game = locked(code); requireVersion(game.getVersion(), request.getExpectedVersion());
        modules.require(code);
        var before = snapshot(game);
        game.changeAvailability(request.getPublished(), request.getAvailability(), request.getMessage()); games.flush();
        audit.write(actor, "GAME_AVAILABILITY_UPDATED", "GAME", code, null, Map.of("before", before, "after", snapshot(game)));
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
                .minHumanPlayers(request.getMinHumanPlayers()).maxHumanPlayers(request.getMaxHumanPlayers()).build()
                : existing.orElseThrow(() -> new BusinessException(ErrorCode.CATALOG_MODE_NOT_FOUND));
        if (create) requireVersion(0, request.getExpectedVersion()); else requireVersion(mode.getVersion(), request.getExpectedVersion());
        var before = create ? Map.<String, Object>of() : snapshot(mode);
        if (!create) mode.update(definition, request.getName(), request.getMinHumanPlayers(), request.getMaxHumanPlayers());
        modes.saveAndFlush(mode);
        audit.write(actor, create ? "GAME_MODE_CREATED" : "GAME_MODE_UPDATED", "GAME_MODE", gameCode + ":" + modeCode,
                null, Map.of("before", before, "after", snapshot(mode)));
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
        audit.write(actor, "GAME_MODE_AVAILABILITY_UPDATED", "GAME_MODE", gameCode + ":" + modeCode, null,
                Map.of("before", before, "after", snapshot(mode)));
        return GameModeResponse.from(mode, true);
    }
    @Transactional
    public GameGenreResponse genre(Long actorId, String code, GameGenreRequest request, boolean create) {
        var actor = requireWrite(actorId, PermissionCode.GAME_WRITE);
        var existing = genres.findForUpdate(code);
        if (create && existing.isPresent()) throw new BusinessException(ErrorCode.GAME_CATALOG_CONFLICT);
        // 생성 시에만 끝 순번을 할당하고 기존 장르의 일반 저장에서는 그리드 순서를 유지한다.
        var genre = create ? GameGenre.builder().code(code).name(request.getName()).sortOrder(ordering.reserveLast(CatalogOrderType.GENRE)).enabled(request.getEnabled()).build()
                : existing.orElseThrow(() -> new BusinessException(ErrorCode.CATALOG_GENRE_NOT_FOUND));
        if (create) requireVersion(0, request.getExpectedVersion()); else requireVersion(genre.getVersion(), request.getExpectedVersion());
        var before = create ? Map.<String, Object>of() : snapshot(genre);
        if (!create) genre.update(request.getName(), request.getEnabled());
        genres.saveAndFlush(genre);
        audit.write(actor, create ? "GAME_GENRE_CREATED" : "GAME_GENRE_UPDATED", "GAME_GENRE", code, null,
                Map.of("before", before, "after", snapshot(genre)));
        return GameGenreResponse.from(genre);
    }
    private AdminContextResponse requireWrite(Long actor, PermissionCode permission) {
        // 기능 권한 편집과도 순서를 맞춘다. 게임 정책 변경은 방/대국 행을 잠그거나 종료하지 않는다.
        roles.lockAuthorizationPolicy().orElseThrow(() -> new BusinessException(ErrorCode.AUTHORIZATION_SERVICE_UNAVAILABLE));
        return authorization.require(actor, permission);
    }
    private Game locked(String code) { return games.findForUpdate(code).orElseThrow(() -> new BusinessException(ErrorCode.CATALOG_GAME_NOT_FOUND)); }
    private Set<GameGenre> validateMetadata(GameMetadataRequest metadata, Set<GameGenre> previous) {
        var assigned = genres.findByCodeIn(metadata.getGenreCodes());
        if (assigned.size() != metadata.getGenreCodes().size()) throw new BusinessException(ErrorCode.CATALOG_GENRE_NOT_FOUND);
        // 사용 중지한 장르의 기존 연결은 유지·제거할 수 있지만 다른 게임에 새로 연결하지는 못한다.
        var previousCodes = previous.stream().map(GameGenre::getCode).collect(Collectors.toSet());
        if (assigned.stream().anyMatch(genre -> !genre.isEnabled() && !previousCodes.contains(genre.getCode()))) {
            throw new BusinessException(ErrorCode.CATALOG_GENRE_DISABLED);
        }
        return new HashSet<>(assigned);
    }
    private static void requireVersion(long actual, long expected) {
        if (actual != expected) throw new BusinessException(ErrorCode.GAME_CATALOG_CHANGED);
    }
    private static Map<String, Object> snapshot(Game game) {
        var values = new LinkedHashMap<String, Object>();
        values.put("code", game.getCode()); values.put("name", game.getName()); values.put("summary", game.getSummary());
        values.put("description", game.getDescription());
        values.put("genres", game.getGenres().stream().map(GameGenre::getCode).sorted().toList()); values.put("sortOrder", game.getSortOrder());
        values.put("published", game.isPublished()); values.put("availability", game.getAvailability().name());
        values.put("message", game.getUnavailableMessage()); values.put("version", game.getVersion()); return values;
    }
    private static Map<String, Object> snapshot(GameMode mode) {
        return Map.of("code", mode.getCode(), "name", mode.getName(), "minHumanPlayers", mode.getMinHumanPlayers(),
                "maxHumanPlayers", mode.getMaxHumanPlayers(), "definitionVersion", mode.getDefinitionVersion(),
                "published", mode.isPublished(), "availability", mode.getAvailability().name(), "message", mode.getUnavailableMessage(), "version", mode.getVersion());
    }
    private static Map<String, Object> snapshot(GameGenre genre) {
        return Map.of("code", genre.getCode(), "name", genre.getName(), "enabled", genre.isEnabled(), "sortOrder", genre.getSortOrder(), "version", genre.getVersion());
    }
}

package com.project.playground.domain.game.service;

import com.project.playground.domain.adminaudit.service.AdminAuditService;
import com.project.playground.domain.authorization.enums.PermissionCode;
import com.project.playground.domain.authorization.repository.RoleRepository;
import com.project.playground.domain.authorization.service.AdminAuthorizationService;
import com.project.playground.domain.game.dto.request.CatalogOrderRequest;
import com.project.playground.domain.game.dto.response.CatalogOrderPageResponse;
import com.project.playground.domain.game.dto.response.CatalogOrderResponse;
import com.project.playground.domain.game.enums.CatalogOrderType;
import com.project.playground.domain.game.repository.CatalogOrderQueryRepository;
import com.project.playground.domain.game.repository.CatalogOrderStateRepository;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** 전체 순서 버전 확인·이동·감사 기록을 함께 커밋한다. 검색 결과나 페이지 일부를 전체 순서로 덮어쓰지 않는다. */
@Service @RequiredArgsConstructor
public class CatalogOrderingService {
    private static final Set<Integer> PAGE_SIZES = Set.of(10, 20, 30, 40, 50);
    private final CatalogOrderStateRepository states;
    private final CatalogOrderQueryRepository queries;
    private final RoleRepository roles;
    private final AdminAuthorizationService authorization;
    private final AdminAuditService audit;

    @Transactional(readOnly = true)
    public CatalogOrderPageResponse page(Long actorId, CatalogOrderType type, int page, int size) {
        if (page < 1 || !PAGE_SIZES.contains(size) || type == CatalogOrderType.FILTER && size == 10) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        // MySQL 반복 읽기의 스냅샷을 만들기 전에 목록을 잠가 버전과 행이 서로 다른 시점을 가리키지 않게 한다.
        var state = states.findForRead(type).orElseThrow(() -> new BusinessException(ErrorCode.GAME_CATALOG_UNAVAILABLE));
        authorization.require(actorId, PermissionCode.GAME_READ);
        long count = queries.count(type);
        int totalPages = Math.toIntExact(Math.max(1, Math.ceilDiv(count, size)));
        int current = Math.min(page, totalPages);
        return CatalogOrderPageResponse.builder().items(queries.page(type, (long) (current - 1) * size, size))
                .page(current).size(size).totalPages(totalPages).totalElements(count).version(state.getVersion()).build();
    }

    /** 등록 명령의 권한 잠금 이후 호출하며 새 항목을 끝에 배치하고 열린 순서 편집의 버전을 무효화한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int reserveLast(CatalogOrderType type) {
        var state = states.findForUpdate(type).orElseThrow(() -> new BusinessException(ErrorCode.GAME_CATALOG_UNAVAILABLE));
        int position = queries.nextPosition(type);
        if (position > 1_000_000) throw new BusinessException(ErrorCode.GAME_CATALOG_CONFLICT);
        state.advance();
        return position;
    }

    @Transactional
    public CatalogOrderResponse reorder(Long actorId, CatalogOrderType type, CatalogOrderRequest request) {
        // 기존 게임·장르·권한 편집과 같은 잠금 순서를 유지한다. 공개·이용 상태 변경 권한으로 순서를 바꾸지 않는다.
        roles.lockAuthorizationPolicy().orElseThrow(() -> new BusinessException(ErrorCode.AUTHORIZATION_SERVICE_UNAVAILABLE));
        var actor = authorization.require(actorId, PermissionCode.GAME_WRITE);
        var state = states.findForUpdate(type).orElseThrow(() -> new BusinessException(ErrorCode.GAME_CATALOG_UNAVAILABLE));
        if (state.getVersion() != request.getExpectedVersion()) throw new BusinessException(ErrorCode.GAME_CATALOG_CHANGED);
        var codes = new LinkedHashSet<String>();
        for (var move : request.getMoves()) {
            if (!codes.add(move.getCode()) || move.getCode().equals(move.getBeforeCode())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
        }
        var before = queries.positions(type, codes);
        if (before.size() != codes.size()) throw new BusinessException(notFound(type));
        boolean changed = false;
        for (var move : request.getMoves()) {
            int source = queries.position(type, move.getCode()).orElseThrow(() -> new BusinessException(notFound(type)));
            int anchor = move.getBeforeCode() == null ? queries.nextPosition(type)
                    : queries.position(type, move.getBeforeCode()).orElseThrow(() -> new BusinessException(notFound(type)));
            int target = anchor > source ? anchor - 1 : anchor;
            if (target != source) {
                queries.move(type, move.getCode(), source, target);
                changed = true;
            }
        }
        if (changed) {
            state.advance();
            audit.write(actor, type == CatalogOrderType.GAME ? "GAME_ORDER_UPDATED" : "GAME_GENRE_ORDER_UPDATED",
                    type == CatalogOrderType.GAME ? "GAME" : "GAME_GENRE", "ORDER", null,
                    Map.of("before", snapshot(before), "after", snapshot(queries.positions(type, codes))));
        }
        return CatalogOrderResponse.builder().version(state.getVersion()).build();
    }

    /** 연결 없는 필터 삭제 후 연속 순번과 순서 버전을 같은 트랜잭션에서 갱신한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void removed(CatalogOrderType type,int position) {
        var state=states.findForUpdate(type).orElseThrow(() -> new BusinessException(ErrorCode.GAME_CATALOG_UNAVAILABLE));
        queries.closeGap(type,position); state.advance();
    }
    private static ErrorCode notFound(CatalogOrderType type) {
        return type == CatalogOrderType.GAME ? ErrorCode.CATALOG_GAME_NOT_FOUND : ErrorCode.CATALOG_GENRE_NOT_FOUND;
    }

    private static Map<String, ?> snapshot(Map<String, Integer> positions) {
        // 이동을 요청한 행의 실제 전후 위치만 기록하고 게임 설명이나 전체 목록을 감사 JSON에 복제하지 않는다.
        return Map.of("order", positions.entrySet().stream().sorted(Map.Entry.comparingByValue())
                .map(entry -> entry.getKey() + " (" + (entry.getValue() + 1) + ")").toList());
    }
}

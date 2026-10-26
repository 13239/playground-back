package com.project.playground.domain.game.service;

import com.project.playground.domain.adminaudit.service.AdminAuditService;
import com.project.playground.domain.authorization.enums.PermissionCode;
import com.project.playground.domain.authorization.repository.RoleRepository;
import com.project.playground.domain.authorization.service.AdminAuthorizationService;
import com.project.playground.domain.game.dto.request.GameFilterRequest;
import com.project.playground.domain.game.dto.response.*;
import com.project.playground.domain.game.entity.GameFilter;
import com.project.playground.domain.game.enums.*;
import com.project.playground.domain.game.repository.*;
import com.project.playground.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

/** 권한 잠금·행 버전·감사를 공유하고 기존 게임 연결은 자동으로 해제하지 않는다. */
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class GameFilterService {
    private static final Set<Integer> SIZES=Set.of(20,30,40,50);
    private final GameFilterRepository filters;
    private final GameCatalogQueryRepository queries;
    private final CatalogOrderStateRepository states;
    private final CatalogOrderingService ordering;
    private final RoleRepository roles;
    private final AdminAuthorizationService authorization;
    private final AdminAuditService audit;

    public AdminCatalogPageResponse<GameFilterResponse> list(Long actor,String search,GameFilterType type,Boolean enabled,int page,int size) {
        if(page<1 || !SIZES.contains(size)) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        var state=states.findForRead(CatalogOrderType.FILTER).orElseThrow(() -> new BusinessException(ErrorCode.GAME_CATALOG_UNAVAILABLE));
        authorization.require(actor,PermissionCode.GAME_READ);
        long count=queries.filterCount(search,type,enabled);
        int pages=Math.toIntExact(Math.max(1,Math.ceilDiv(count,size))),current=Math.min(page,pages);
        var rows=queries.filters(search,type,enabled,(long)(current-1)*size,size);
        var counts=counts(rows);
        return AdminCatalogPageResponse.<GameFilterResponse>builder().items(rows.stream().map(f -> GameFilterResponse.from(f,counts.getOrDefault(f.getLabelCode(),0L))).toList())
                .page(current).size(size).totalPages(pages).totalElements(count).orderVersion(state.getVersion()).build();
    }
    public GameFilterResponse get(Long actor,String code) {
        authorization.require(actor,PermissionCode.GAME_READ);
        return response(filters.findByCode(code).orElseThrow(() -> new BusinessException(ErrorCode.GAME_FILTER_NOT_FOUND)));
    }
    public GameFilterPageResponse options(Long actor,String search,String cursor,int limit,boolean publicOnly) {
        if(!publicOnly) authorization.require(actor,PermissionCode.GAME_READ);
        int bounded=Math.clamp(limit,1,100); var rows=queries.filterCursor(search,cursor,bounded,publicOnly);
        var last=rows.size()>bounded ? rows.get(bounded-1) : null;
        return GameFilterPageResponse.builder().items(rows.stream().limit(bounded).map(f -> GameFilterResponse.from(f,0)).toList())
                .nextCursor(last==null ? null : CatalogCursor.encode(last.getSortOrder(),last.getId())).build();
    }
    @Transactional
    public GameFilterResponse save(Long actorId,String code,GameFilterRequest request,boolean create) {
        roles.lockAuthorizationPolicy().orElseThrow(() -> new BusinessException(ErrorCode.AUTHORIZATION_SERVICE_UNAVAILABLE));
        var actor=authorization.require(actorId,PermissionCode.GAME_WRITE); var existing=filters.findForUpdate(code);
        if(create && existing.isPresent()) throw new BusinessException(ErrorCode.GAME_CATALOG_CONFLICT);
        var f=create ? GameFilter.builder().code(code).name(request.getName()).type(request.getType()).icon(request.getIcon()).enabled(request.getEnabled())
                .minPlayers(request.getMinPlayers()).maxPlayers(request.getMaxPlayers()).graphicsType(request.getGraphicsType())
                .sortOrder(ordering.reserveLast(CatalogOrderType.FILTER)).build()
                : existing.orElseThrow(() -> new BusinessException(ErrorCode.GAME_FILTER_NOT_FOUND));
        if((create ? 0L : f.getVersion())!=request.getExpectedVersion()) throw new BusinessException(ErrorCode.GAME_CATALOG_CHANGED);
        if(f.getType()!=request.getType()) throw new BusinessException(ErrorCode.GAME_FILTER_CONFIGURATION_INVALID);
        var before=create ? Map.<String,Object>of() : snapshot(f);
        // 연결 중인 필터는 표시 정보만 수정하고 실제 인원·그래픽 조건의 재해석을 막는다.
        if(!create && (!Objects.equals(f.getMinPlayers(),request.getMinPlayers()) || !Objects.equals(f.getMaxPlayers(),request.getMaxPlayers())
                || f.getGraphicsType()!=request.getGraphicsType()) && count(f)>0) throw new BusinessException(ErrorCode.GAME_FILTER_IN_USE);
        f.update(request.getName(),request.getEnabled()); f.configure(request.getIcon(),request.getMinPlayers(),request.getMaxPlayers(),request.getGraphicsType());
        filters.saveAndFlush(f);
        audit.write(actor,create ? "GAME_FILTER_CREATED" : "GAME_FILTER_UPDATED","GAME_FILTER",code,null,Map.of("before",before,"after",snapshot(f)));
        return response(f);
    }
    @Transactional
    public void delete(Long actorId,String code,long expectedVersion) {
        roles.lockAuthorizationPolicy().orElseThrow(() -> new BusinessException(ErrorCode.AUTHORIZATION_SERVICE_UNAVAILABLE));
        var actor=authorization.require(actorId,PermissionCode.GAME_WRITE);
        var f=filters.findForUpdate(code).orElseThrow(() -> new BusinessException(ErrorCode.GAME_FILTER_NOT_FOUND));
        if(f.getVersion()!=expectedVersion) throw new BusinessException(ErrorCode.GAME_CATALOG_CHANGED);
        // 라벨 선택과 과거 장르 연결을 모두 확인해 연결된 게임 데이터는 삭제하거나 해제하지 않는다.
        if(count(f)>0) throw new BusinessException(ErrorCode.GAME_FILTER_IN_USE);
        var before=snapshot(f); int position=f.getSortOrder();
        filters.delete(f); filters.flush(); ordering.removed(CatalogOrderType.FILTER,position);
        audit.write(actor,"GAME_FILTER_DELETED","GAME_FILTER",code,null,Map.of("before",before));
    }
    private Map<String,Long> counts(List<GameFilter> rows) {
        if(rows.isEmpty()) return Map.of();
        return filters.countLinks(rows.stream().map(GameFilter::getLabelCode).toList()).stream()
                .collect(Collectors.toMap(GameFilterRepository.LinkCount::getLabelCode,GameFilterRepository.LinkCount::getGameCount));
    }
    private long count(GameFilter f) { return counts(List.of(f)).getOrDefault(f.getLabelCode(),0L); }
    private GameFilterResponse response(GameFilter f) { return GameFilterResponse.from(f,count(f)); }
    private Map<String,Object> snapshot(GameFilter f) {
        var data=new LinkedHashMap<String,Object>(); data.put("code",f.getCode()); data.put("name",f.getName()); data.put("type",f.getType());
        data.put("icon",f.getIcon()); data.put("enabled",f.isEnabled()); data.put("minPlayers",f.getMinPlayers()); data.put("maxPlayers",f.getMaxPlayers());
        data.put("graphicsType",f.getGraphicsType()); data.put("version",f.getVersion()); return data;
    }
}

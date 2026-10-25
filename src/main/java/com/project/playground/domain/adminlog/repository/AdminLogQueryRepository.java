package com.project.playground.domain.adminlog.repository;

import com.project.playground.domain.activitylog.entity.QActivityLog;
import com.project.playground.domain.activitylog.enums.*;
import com.project.playground.domain.adminaudit.entity.QAdminAuditLog;
import com.project.playground.domain.adminlog.dto.*;
import com.project.playground.domain.adminlog.dto.internal.LogDetailSource;
import com.project.playground.domain.adminlog.support.*;
import com.project.playground.domain.authorization.entity.QRole;
import com.project.playground.domain.game.entity.*;
import com.project.playground.domain.user.entity.QUser;
import com.project.playground.domain.user.enums.ProfileAvatar;
import com.project.playground.global.exception.*;
import com.querydsl.core.*;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.util.stream.Stream;

/** 목록은 표시 컬럼만, 상세는 선택한 한 건만 읽는다. 대상 이름은 페이지 단위 일괄 조회해 N+1을 만들지 않는다. */
@Repository @RequiredArgsConstructor
public class AdminLogQueryRepository {
    private static final QActivityLog A = QActivityLog.activityLog;
    private static final QAdminAuditLog D = QAdminAuditLog.adminAuditLog;
    private static final QUser U = new QUser("logPerson");
    private static final QUser S = new QUser("logSearchPerson");
    private static final QRole R = QRole.role;
    // 폐기되거나 아직 이 서버가 모르는 활동도 enum 역직렬화로 목록 전체를 막지 않는다.
    private static final StringExpression ACTIVITY_TYPE = A.type.stringValue();
    private static final Expression<?>[] ACTIVITY = { A.id, A.occurredAt, A.userId, ACTIVITY_TYPE, A.result, A.clientIp,
            A.failureCode, A.searchUsername, A.searchNickname, U.username, U.nickname, U.profileAvatar };
    private static final Expression<?>[] AUDIT = { D.id, D.occurredAt, D.actorUserId, D.actorRoleCode, R.name,
            D.action, D.targetType, D.targetId, D.reason, U.username, U.nickname, U.profileAvatar };
    private final JPAQueryFactory query;

    public LogPageResponse<ActivityLogSummary> activities(LogSearch search) {
        var where = activityConditions(search);
        long total = count(query.select(A.id.count()).from(A).where(where));
        int pages = pages(total, search.getSize()), page = Math.min(search.getPage(), pages);
        var items = activitiesBase().where(where).orderBy(A.occurredAt.desc(), A.id.desc())
                .offset((long) (page - 1) * search.getSize()).limit(search.getSize()).fetch().stream().map(this::activity).toList();
        return LogPageResponse.<ActivityLogSummary>builder().items(items).page(page).size(search.getSize()).totalPages(pages).totalElements(total).build();
    }
    public LogPageResponse<AuditLogSummary> audits(LogSearch search) {
        var where = auditConditions(search);
        long total = count(query.select(D.id.count()).from(D).where(where));
        int pages = pages(total, search.getSize()), page = Math.min(search.getPage(), pages);
        var rows = auditsBase().where(where).orderBy(D.occurredAt.desc(), D.id.desc())
                .offset((long) (page - 1) * search.getSize()).limit(search.getSize()).fetch();
        var entries = rows.stream().map(this::audit).toList();
        var names = targetNames(entries);
        var items = entries.stream().map(entry -> entry.toBuilder().targetName(names.getOrDefault(targetKey(entry), entry.getTargetName())).build()).toList();
        return LogPageResponse.<AuditLogSummary>builder().items(items).page(page).size(search.getSize()).totalPages(pages).totalElements(total).build();
    }
    public LogDetailSource<ActivityLogSummary> activity(long id) {
        var row = activitiesBase(A.detail, A.userAgent).where(A.id.eq(id)).fetchOne();
        if (row == null) throw new BusinessException(ErrorCode.ADMIN_LOG_NOT_FOUND);
        return LogDetailSource.<ActivityLogSummary>builder().entry(activity(row)).detail(row.get(A.detail)).userAgent(row.get(A.userAgent)).build();
    }
    public LogDetailSource<AuditLogSummary> audit(long id) {
        var row = auditsBase(D.detail).where(D.id.eq(id)).fetchOne();
        if (row == null) throw new BusinessException(ErrorCode.ADMIN_LOG_NOT_FOUND);
        var entry = audit(row);
        var name = targetNames(List.of(entry)).getOrDefault(targetKey(entry), entry.getTargetName());
        return LogDetailSource.<AuditLogSummary>builder().entry(entry.toBuilder().targetName(name).build()).detail(row.get(D.detail)).build();
    }
    public Map<String, String> roleNames(Set<String> codes) {
        if (codes.isEmpty()) return Map.of();
        var names = new HashMap<String, String>();
        timed(query.select(R.code, R.name).from(R).where(R.code.in(codes))).fetch().forEach(row -> names.put(row.get(R.code), row.get(R.name)));
        return names;
    }
    private BooleanBuilder activityConditions(LogSearch search) {
        var where = new BooleanBuilder(A.occurredAt.goe(search.getFrom()).and(A.occurredAt.lt(search.getUntil())));
        if (search.getType() != null) where.and(A.type.eq(ActivityType.valueOf(search.getType())));
        if (search.getResult() != null) where.and(A.result.eq(ActivityResult.valueOf(search.getResult())));
        if (search.getQuery() != null) {
            String prefix = search.prefix();
            // 당시 입력한 식별자와 현재 회원 정보를 함께 검색해 실패·닉네임 변경 이후의 기록도 찾는다.
            var matches = new BooleanBuilder(A.searchUsername.like(prefix, '!').or(A.searchNickname.like(prefix, '!'))
                    .or(A.userId.in(JPAExpressions.select(S.id).from(S).where(S.username.like(prefix, '!').or(S.nickname.like(prefix, '!'))))));
            if (search.memberId() != null) matches.or(A.userId.eq(search.memberId()));
            where.and(matches);
        }
        return where;
    }
    private BooleanBuilder auditConditions(LogSearch search) {
        var where = new BooleanBuilder(D.occurredAt.goe(search.getFrom()).and(D.occurredAt.lt(search.getUntil())));
        if (search.getType() != null) where.and(D.action.eq(search.getType()));
        if (search.getTargetType() != null) where.and(D.targetType.eq(search.getTargetType()));
        if (search.getQuery() != null) {
            var matches = new BooleanBuilder(D.actorUserId.in(JPAExpressions.select(S.id).from(S)
                    .where(S.username.like(search.prefix(), '!').or(S.nickname.like(search.prefix(), '!')))));
            if (search.memberId() != null) matches.or(D.actorUserId.eq(search.memberId()));
            where.and(matches);
        }
        return where;
    }
    private JPAQuery<Tuple> activitiesBase(Expression<?>... extra) {
        return timed(query.select(columns(ACTIVITY, extra)).from(A).leftJoin(U).on(A.userId.eq(U.id)));
    }
    private JPAQuery<Tuple> auditsBase(Expression<?>... extra) {
        return timed(query.select(columns(AUDIT, extra)).from(D).leftJoin(U).on(D.actorUserId.eq(U.id)).leftJoin(R).on(D.actorRoleCode.eq(R.code)));
    }
    private ActivityLogSummary activity(Tuple row) {
        String type = row.get(ACTIVITY_TYPE), result = row.get(A.result).name(), failure = row.get(A.failureCode);
        var person = LogPerson.builder().id(row.get(A.userId)).username(first(row.get(A.searchUsername), row.get(U.username)))
                .nickname(first(row.get(A.searchNickname), row.get(U.nickname))).avatarId(avatar(row)).build();
        return ActivityLogSummary.builder().id(row.get(A.id)).occurredAt(row.get(A.occurredAt)).member(person)
                .type(type).typeName(AdminLogCatalog.activity(type)).result(result).resultName(AdminLogCatalog.result(result))
                .clientIp(row.get(A.clientIp)).failureCode(failure).failureMessage(AdminLogCatalog.failure(failure)).build();
    }
    private AuditLogSummary audit(Tuple row) {
        String action = row.get(D.action), type = row.get(D.targetType), id = row.get(D.targetId), role = row.get(D.actorRoleCode);
        return AuditLogSummary.builder().id(row.get(D.id)).occurredAt(row.get(D.occurredAt))
                .actor(LogPerson.builder().id(row.get(D.actorUserId)).username(row.get(U.username)).nickname(row.get(U.nickname)).avatarId(avatar(row)).build())
                .actorRoleCode(role).actorRoleName(first(row.get(R.name), role)).action(action).actionName(AdminLogCatalog.action(action))
                .targetType(type).targetId(id).targetName(type.equals("ACCESS_POLICY") ? "운영 설정" : AdminLogCatalog.target(type) + " · " + id).reason(row.get(D.reason)).build();
    }
    private Map<String, String> targetNames(List<AuditLogSummary> entries) {
        var grouped = new HashMap<String, Set<String>>();
        entries.forEach(entry -> grouped.computeIfAbsent(entry.getTargetType(), ignored -> new HashSet<>()).add(entry.getTargetId()));
        var names = new HashMap<String, String>();
        Set<Long> memberIds = new HashSet<>();
        grouped.getOrDefault("USER", Set.of()).forEach(id -> { try { if (id.matches("[1-9]\\d{0,18}")) memberIds.add(Long.valueOf(id)); } catch (NumberFormatException ignored) { /* 오래된 잘못된 대상도 식별 코드로 표시한다. */ } });
        if (!memberIds.isEmpty()) timed(query.select(U.id, U.nickname).from(U).where(U.id.in(memberIds))).fetch()
                .forEach(row -> names.put("USER:" + row.get(U.id), row.get(U.nickname) + " · 회원 #" + row.get(U.id)));
        var games = QGame.game;
        if (grouped.containsKey("GAME")) timed(query.select(games.code, games.name).from(games).where(games.code.in(grouped.get("GAME")))).fetch()
                .forEach(row -> names.put("GAME:" + row.get(games.code), row.get(games.name) + " (" + row.get(games.code) + ")"));
        var genres = QGameGenre.gameGenre;
        if (grouped.containsKey("GAME_GENRE")) timed(query.select(genres.code, genres.name).from(genres).where(genres.code.in(grouped.get("GAME_GENRE")))).fetch()
                .forEach(row -> names.put("GAME_GENRE:" + row.get(genres.code), row.get(genres.name) + " (" + row.get(genres.code) + ")"));
        if (grouped.containsKey("ROLE")) roleNames(grouped.get("ROLE")).forEach((code, name) -> names.put("ROLE:" + code, name + " (" + code + ")"));
        if (grouped.containsKey("GAME_MODE")) {
            var modes = QGameMode.gameMode;
            // 한 게임의 모든 모드를 읽지 않고 이번 페이지에 등장한 코드 쌍만 조회한다.
            var modeTargets = new BooleanBuilder();
            grouped.get("GAME_MODE").forEach(id -> { String[] pair = id.split(":", 2); if (pair.length == 2) modeTargets.or(games.code.eq(pair[0]).and(modes.code.eq(pair[1]))); });
            if (modeTargets.hasValue()) timed(query.select(games.code, games.name, modes.code, modes.name).from(modes).join(games).on(modes.gameId.eq(games.id))
                    .where(modeTargets)).fetch().forEach(row -> {
                String id = row.get(games.code) + ":" + row.get(modes.code);
                if (grouped.get("GAME_MODE").contains(id)) names.put("GAME_MODE:" + id, row.get(games.name) + " · " + row.get(modes.name));
            });
        }
        return names;
    }
    private static String targetKey(AuditLogSummary entry) { return entry.getTargetType() + ":" + entry.getTargetId(); }
    private static String first(String primary, String fallback) { return primary == null ? fallback : primary; }
    // 삭제되거나 연결되지 않은 로그 작성자도 현재 카탈로그의 5번 기본 캐릭터로 표시한다.
    private static ProfileAvatar avatar(Tuple row) { return row.get(U.profileAvatar) == null ? ProfileAvatar.DEFAULT : row.get(U.profileAvatar); }
    private static Expression<?>[] columns(Expression<?>[] base, Expression<?>[] extra) { return Stream.concat(Arrays.stream(base), Arrays.stream(extra)).toArray(Expression<?>[]::new); }
    private static <T> JPAQuery<T> timed(JPAQuery<T> query) { return query.setHint("jakarta.persistence.query.timeout", 3000); }
    private static long count(JPAQuery<Long> query) { Long count = timed(query).fetchOne(); return count == null ? 0 : count; }
    private static int pages(long total, int size) { return Math.max(1, Math.toIntExact(Math.ceilDiv(total, size))); }
}

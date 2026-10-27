package com.project.playground.domain.usermanagement.repository;

import com.project.playground.domain.usermanagement.dto.*;
import com.project.playground.domain.user.enums.UserStatus;
import com.project.playground.domain.user.entity.QUser;
import com.project.playground.domain.authorization.entity.QRole;
import com.project.playground.global.exception.*;
import com.querydsl.core.*;
import com.querydsl.core.types.dsl.*;
import com.querydsl.core.types.Expression;
import com.querydsl.jpa.impl.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.util.stream.Stream;

/** 필요한 컬럼만 선택한다. 비밀번호/토큰과 회원별 별도 쿼리를 제외하고 동일 필터의 집계로 번호 페이지를 계산한다. */
@Repository @RequiredArgsConstructor
public class AdminUserQueryRepository {
    private final JPAQueryFactory query;
    private static final QUser U = QUser.user;
    private static final QRole R = QRole.role;
    private static final Expression<?>[] SUMMARY = { U.id, U.username, U.nickname, U.profileAvatar, U.status, U.deletedAt,
            U.createdAt, U.updatedAt, U.managementVersion, U.roleVersion, R.code, R.name };

    public AdminUserPage list(String search, String status, String roleCode, int page, int size) {
        // 전체 상태에는 탈퇴도 포함하며 삭제 시각이 기록된 계정은 모든 상태 조회에서 제외한다.
        var where = new BooleanBuilder(U.deletedAt.isNull());
        if (status != null) where.and(U.status.eq(UserStatus.valueOf(status)));
        if (roleCode != null) where.and(U.roleCode.eq(roleCode));
        if (search != null && !search.isBlank()) {
            // 기본 B-tree 인덱스를 사용할 수 있는 앞부분 검색이다. %, _, !는 일반 문자로 취급한다.
            String text = search.strip();
            String prefix = text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            var matches = new BooleanBuilder(U.username.like(prefix, '!').or(U.nickname.like(prefix, '!')));
            if (text.matches("[1-9]\\d{0,18}")) { try { matches.or(U.id.eq(Long.parseLong(text))); } catch (NumberFormatException ignored) { /* 큰 숫자도 이름의 앞부분으로 검색한다. */ } }
            where.and(matches);
        }
        // 집계는 역할 조인 없이 같은 조건으로 한 번만 실행하고 필요한 행만 읽는다.
        Long count = query.select(U.id.count()).from(U).where(where).setHint("jakarta.persistence.query.timeout", 3000).fetchOne();
        long total = count == null ? 0 : count;
        int totalPages = Math.max(1, Math.toIntExact(Math.ceilDiv(total, size)));
        int actualPage = Math.min(page, totalPages);
        var items = base().where(where).orderBy(U.id.desc()).offset((long) (actualPage - 1) * size).limit(size)
                .fetch().stream().map(this::summary).toList();
        return AdminUserPage.builder().items(items).page(actualPage).size(size).totalPages(totalPages).totalElements(total).build();
    }
    public AdminUserDetails details(long id) {
        // 삭제된 계정은 ID를 직접 지정해도 회원 상세로 반환하지 않는다.
        var row = base().where(U.id.eq(id), U.deletedAt.isNull()).fetchOne();
        if (row == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        return AdminUserDetails.builder().user(summary(row)).build();
    }
    public AdminUserSensitive sensitive(long id) {
        var row = query.select(U.id, U.email, U.phone, U.birthday, U.gender, U.managementVersion)
                // 탈퇴·삭제 계정의 개인정보 원문 조회 제한은 상태 코드 분리 후에도 유지한다.
                .from(U).where(U.id.eq(id), U.deletedAt.isNull(), U.status.ne(UserStatus.WITHDRAWN)).fetchOne();
        if (row == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        return AdminUserSensitive.builder().userId(row.get(U.id)).email(row.get(U.email)).phone(row.get(U.phone))
                .birthday(row.get(U.birthday)).gender(row.get(U.gender)).managementVersion(row.get(U.managementVersion)).build();
    }
    private JPAQuery<Tuple> base(Expression<?>... extra) {
        return query.select(Stream.concat(Arrays.stream(SUMMARY), Arrays.stream(extra)).toArray(Expression<?>[]::new))
                .from(U).leftJoin(R).on(U.roleCode.eq(R.code))
                .setHint("jakarta.persistence.query.timeout", 3000);
    }
    private AdminUserSummary summary(Tuple row) {
        return AdminUserSummary.builder().id(row.get(U.id)).username(row.get(U.username)).nickname(row.get(U.nickname))
                .avatarId(row.get(U.profileAvatar)).status(row.get(U.status)).deleted(row.get(U.deletedAt) != null)
                .roleCode(row.get(R.code)).roleName(row.get(R.name)).createdAt(row.get(U.createdAt)).updatedAt(row.get(U.updatedAt))
                .managementVersion(row.get(U.managementVersion)).roleVersion(row.get(U.roleVersion)).build();
    }
}

package com.project.playground.domain.gomoku.repository;

import com.project.playground.domain.gomoku.dto.request.SearchRoomsRequest;
import com.project.playground.domain.gomoku.dto.response.RoomSummaryResponse;
import com.project.playground.domain.gomoku.enums.RoomStatus;
import com.project.playground.domain.gomoku.enums.RoomVisibility;
import com.project.playground.domain.user.enums.UserStatus;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Locale;

import static com.project.playground.domain.gomoku.entity.QGomokuRoom.gomokuRoom;
import static com.project.playground.domain.user.entity.QUser.user;

/** 목록에 필요한 컬럼만 한 번의 조인으로 읽어 회원 비밀번호 조회와 N+1 쿼리를 피한다. */
@Repository
@RequiredArgsConstructor
public class GomokuRoomQueryRepository {
    public static final int PAGE_SIZE = 6;
    private final JPAQueryFactory query;

    public long count(SearchRoomsRequest request) {
        Long count = query.select(gomokuRoom.count()).from(gomokuRoom).join(gomokuRoom.host, user)
                .where(filters(request)).fetchOne();
        return count == null ? 0 : count;
    }

    public List<RoomSummaryResponse> search(Long userId, SearchRoomsRequest request, int page) {
        var room = gomokuRoom;
        return query.select(room.id, room.title, room.visibility, room.playerCount, room.undoLimit,
                        room.forbiddenRule, room.status, user.id, user.nickname, user.profileAvatar)
                .from(room).join(room.host, user).where(filters(request)).orderBy(room.id.desc())
                .offset((long) page * PAGE_SIZE).limit(PAGE_SIZE).fetch().stream()
                .map(row -> RoomSummaryResponse.builder().id(row.get(room.id)).title(row.get(room.title))
                        .visibility(row.get(room.visibility)).playerCount(row.get(room.playerCount))
                        .undoLimit(row.get(room.undoLimit)).forbiddenRule(row.get(room.forbiddenRule))
                        .status(row.get(room.status)).hostNickname(row.get(user.nickname)).hostAvatarId(row.get(user.profileAvatar))
                        .owned(userId.equals(row.get(user.id))).build()).toList();
    }

    private BooleanBuilder filters(SearchRoomsRequest request) {
        var where = new BooleanBuilder(gomokuRoom.status.ne(RoomStatus.CLOSED))
                .and(user.status.eq(UserStatus.ACTIVE)).and(user.deletedAt.isNull());
        if (request.isPublicOnly()) where.and(gomokuRoom.visibility.eq(RoomVisibility.PUBLIC));
        if (request.isWaitingOnly()) where.and(gomokuRoom.status.eq(RoomStatus.WAITING));
        String term = request.getQuery() == null ? "" : request.getQuery().strip();
        if (!term.isEmpty()) {
            // Querydsl의 containsIgnoreCase는 사용자 입력의 LIKE 와일드카드를 이스케이프한다.
            var titleMatches = gomokuRoom.title.containsIgnoreCase(term);
            // ASCII 전용 코드 컬럼에는 유효한 6자리 코드만 비교해 한글 검색의 문자셋 충돌을 피한다.
            String code = term.toUpperCase(Locale.ROOT);
            where.and(code.matches("[A-HJ-NP-Z2-9]{6}")
                    ? titleMatches.or(gomokuRoom.inviteCode.eq(code)) : titleMatches);
        }
        return where;
    }
}

package com.project.playground.domain.social;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Collection;
import java.util.Set;

/** 다른 도메인도 친구·차단 원본과 같은 변경 방송을 사용하고 관계 SQL을 복제하지 않는다. */
@Service @RequiredArgsConstructor
public class SocialFriendshipPolicy {
    private final SocialRepository repository;
    private final SocialProjector projector;

    public boolean friends(long first, long second) {
        if (first == second || repository.user(first) == null || repository.user(second) == null) return false;
        var relationship = repository.relation(first, second);
        return relationship != null && "ACCEPTED".equals(relationship.getState()) && !repository.blocked(first, second);
    }

    /** 방 목록의 친구 표시를 한 번에 조회해 방마다 관계·차단 SQL을 반복하지 않는다. */
    public Set<Long> friendIds(long user, Collection<Long> candidates) {
        return candidates.isEmpty() ? Set.of() : repository.friendIds(user, candidates);
    }

    /** 호출자의 트랜잭션과 같은 outbox에 보존해 연결 장애 중에도 전달을 복구한다. */
    public void changed(Collection<Long> users) {
        users.stream().distinct().forEach(repository::outboxUser);
        projector.afterCommit();
    }
}

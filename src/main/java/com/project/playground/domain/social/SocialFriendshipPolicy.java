package com.project.playground.domain.social;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Collection;

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

    /** 호출자의 트랜잭션과 같은 outbox에 보존해 연결 장애 중에도 전달을 복구한다. */
    public void changed(Collection<Long> users) {
        users.stream().distinct().forEach(repository::outboxUser);
        projector.afterCommit();
    }
}

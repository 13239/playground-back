package com.project.playground.domain.accesspolicy.service;

import com.project.playground.domain.accesspolicy.redis.RedisAccessPolicyRepository;
import com.project.playground.domain.accesspolicy.repository.AccessPolicyRepository;
import com.project.playground.domain.authorization.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** 공통 SQL 잠금이 커밋 순서·다중 서버 발행 순서를 일치시키며 pending/Redis 유실을 복구한다. */
@Slf4j @Component @RequiredArgsConstructor
public class AccessPolicySynchronizer implements ApplicationRunner {
    private final TransactionTemplate transactions;
    private final RoleRepository roles;
    private final AccessPolicyRepository policies;
    private final RedisAccessPolicyRepository redis;

    @Override public void run(ApplicationArguments args) { synchronize(); }

    public void synchronize() {
        transactions.executeWithoutResult(status -> {
            roles.lockAuthorizationPolicy().orElseThrow();
            redis.synchronize(policies.findById(1L).orElseThrow());
        });
    }

    @Scheduled(fixedDelay = 5_000, initialDelay = 5_000)
    public void recover() {
        try { synchronize(); }
        catch (RuntimeException exception) {
            // 장애 때 기존 허용 정책으로 우회하지 않는다. 비밀 값·관리자 입력 원문도 로그에 넣지 않는다.
            log.warn("접속 제한 정책 동기화를 완료하지 못했습니다. 다음 주기에 재시도합니다.");
        }
    }
}

package com.project.playground.domain.social;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static com.project.playground.domain.social.SocialRows.*;

/** MySQL 원본에서 방 권한을 재구축한다. 취소/퇴장 전 캐시를 지우고 커밋 뒤 최신 행 잠금 아래 투영한다. */
@Slf4j @Component @RequiredArgsConstructor
class SocialProjector {
    private final SocialRepository repository;
    private final StringRedisTemplate redis;
    private final JsonMapper mapper;
    private final PlatformTransactionManager transactions;
    private TransactionTemplate transaction() {
        var value=new TransactionTemplate(transactions);
        value.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        value.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return value;
    }
    long nowMicros() {
        // 채팅 저장과 입장 경계가 같은 Redis 시계를 사용하도록 한다.
        var script=org.springframework.data.redis.core.script.RedisScript.of("local t=redis.call('TIME'); return t[1]*1000000+t[2]",Long.class);
        Long value=redis.execute(script,List.of());
        if(value==null) throw new IllegalStateException("social clock unavailable");
        return value;
    }
    void invalidate(List<String> rooms) {
        if(rooms.isEmpty()) return;
        // 원본 행 잠금은 호출자가 정렬된 순서로 확보한다. 롤백돼도 닫힌 캐시는 복구 작업으로 다시 만든다.
        var copy=List.copyOf(rooms);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if(status==STATUS_ROLLED_BACK) {
                    try { transaction().executeWithoutResult(s -> copy.forEach(repository::outboxRoom)); }
                    catch(RuntimeException ignored) { log.warn("친구 권한 복구 작업을 예약하지 못했습니다."); }
                }
            }
        });
        redis.delete(copy.stream().map(id->"social:room:"+id).toList());
    }
    void afterCommit() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { drain(); }
        });
    }
    void warm(long user) {
        for(String room:repository.roomsFor(user)) if(!Boolean.TRUE.equals(redis.hasKey("social:room:"+room))) project(room);
    }
    @Scheduled(fixedDelay=1000,scheduler="socialProjectionScheduler")
    public void drain() {
        try {
            var tasks=repository.list("SELECT id,room_id,user_id FROM tbl_social_outbox ORDER BY id LIMIT 100",(r,n)->Map.of("id",r.getLong(1),"room",Objects.toString(r.getString(2),""),"user",r.getLong(3)));
            for(var task:tasks) {
                transaction().executeWithoutResult(status -> {
                    long id=((Number)task.get("id")).longValue();
                    // 같은 outbox 작업의 다중 인스턴스 소비도 멱등이며 현재 원본만 재조회한다.
                    var exists=repository.list("SELECT id FROM tbl_social_outbox WHERE id=? FOR UPDATE",(r,n)->r.getLong(1),id);
                    if(exists.isEmpty()) return;
                    String room=(String)task.get("room");
                    if(!room.isEmpty()) projectLocked(room);
                    long user=((Number)task.get("user")).longValue();
                    if(user>0) redis.convertAndSend("social:changed:v1",Long.toString(user));
                    repository.update("DELETE FROM tbl_social_outbox WHERE id=?",id);
                });
            }
        } catch(RuntimeException exception) {
            // DB 커밋은 성공했다. outbox를 보존해 Redis 복구 후 재시도하며 접근 불명 방은 닫힌 상태로 둔다.
            log.warn("친구 상태 전달을 지연합니다. 저장된 작업은 다시 처리합니다.");
        }
    }
    private void project(String room) { transaction().executeWithoutResult(status->projectLocked(room)); }
    private void projectLocked(String id) {
        Room room=repository.room(id,true);
        if(room==null) return;
        var members=repository.members(id);
        boolean writable=!room.isClosed();
        if(room.getKind().equals("DIRECT") && members.size()==2) {
            var relation=repository.relation(members.get(0).getUserId(),members.get(1).getUserId());
            writable &= relation!=null && relation.getState().equals("ACCEPTED")
                && !repository.blocked(members.get(0).getUserId(),members.get(1).getUserId());
        }
        Map<String,String> permissions=new LinkedHashMap<>();
        for(Member member:members) {
            long user=member.getUserId();
            if(member.isActive()&&!room.isClosed()&&repository.user(user)!=null) {
                permissions.put(Long.toString(user),mapper.writeValueAsString(Map.of(
                    "since",member.getSince(),"until",room.getUntil(),"writable",writable,
                    "membershipId",member.getMembershipId(),"blocked",repository.blockedAuthors(user),"muted",member.isMuted(),"kind",room.getKind())));
                redis.opsForSet().add("social:rooms:"+user,id);
            } else redis.opsForSet().remove("social:rooms:"+user,id);
        }
        // 방의 각 회원 권한을 원자적으로 교체한다. 중간 HSET 상태에서 일부만 허용하지 않는다.
        var arguments=new ArrayList<String>();
        permissions.forEach((key,value)->{arguments.add(key);arguments.add(value);});
        var replace=org.springframework.data.redis.core.script.RedisScript.of(
            "redis.call('DEL',KEYS[1]); if #ARGV>0 then redis.call('HSET',KEYS[1],unpack(ARGV)) end; return 1",Long.class);
        redis.execute(replace,List.of("social:room:"+id),arguments.toArray());
        members.forEach(m->redis.convertAndSend("social:changed:v1",Long.toString(m.getUserId())));
    }
    /** Redis 전달 지연이 게임 정리·타이머 스케줄러를 막지 않도록 전용 실행기를 둔다. */
    @Configuration(proxyBeanMethods=false)
    static class Scheduling {
        @Bean(defaultCandidate=false)
        ThreadPoolTaskScheduler socialProjectionScheduler() {
            var scheduler=new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(1);
            scheduler.setThreadNamePrefix("social-projection-");
            scheduler.setWaitForTasksToCompleteOnShutdown(true);
            scheduler.setAwaitTerminationSeconds(5);
            return scheduler;
        }
    }
}


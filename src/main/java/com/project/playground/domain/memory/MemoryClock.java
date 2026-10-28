package com.project.playground.domain.memory;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.connection.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** 활성 방의 가장 이른 마감만 메모리에 유지한다. 유휴 중에는 DB 폴링을 수행하지 않는다. */
@Component @RequiredArgsConstructor @Slf4j
public class MemoryClock implements MessageListener, SubscriptionListener {
    private final MemoryService games;
    private final MemoryRoomRepository rooms;
    private final Clock clock;
    private final Map<Long,Due> byRoom=new HashMap<>();
    private final TreeSet<Due> queue=new TreeSet<>(Comparator.comparing((Due d)->d.at).thenComparing(d->d.id));
    private volatile boolean ready;
    private synchronized void schedule(Long id,Instant at) {
        Due old=byRoom.get(id);
        // Redis의 늦은 접속 예약이 더 이른 오답·턴 마감을 덮어쓰지 않는다. 이른 예약은 원본을 한 번 읽고 갱신한다.
        if(old!=null&&at!=null&&!old.at.isAfter(at))return;
        byRoom.remove(id); if(old!=null)queue.remove(old);
        if(at!=null){var due=new Due(id,at);byRoom.put(id,due);queue.add(due);}
    }
    @TransactionalEventListener public void committed(MemoryEvents.ClockChanged event) { schedule(event.getRoomId(),event.getDeadline()); }
    @EventListener(ApplicationReadyEvent.class) public void started(){ready=true;recover();}
    private synchronized void recover() {
        long after=0;
        while(true){var batch=rooms.recover(after,PageRequest.of(0,100));batch.forEach(r->schedule(r.getId(),r.nextDeadline()));
            if(batch.size()<100)break;after=batch.getLast().getId();}
    }
    @Override public void onChannelSubscribed(byte[] channel,long count){if(ready)recover();}
    @Override public void onMessage(Message message,byte[] pattern){
        try{String[] data=new String(message.getBody(),StandardCharsets.UTF_8).split("\\|",-1);
            if(data.length==2)schedule(Long.valueOf(data[0]),data[1].isEmpty()?null:Instant.parse(data[1]));
        }catch(IllegalArgumentException|DateTimeException ignored){/* 내부 토픽도 형식이 확인된 예약만 수용한다. */}
    }
    @Scheduled(fixedDelay=100) public void tick(){
        // 한 주기에 제한된 방만 처리해 공유 스케줄러의 다른 게임 시계를 지연시키지 않는다.
        for(int i=0;i<100;i++){
            Due due;
            synchronized(this){if(queue.isEmpty()||queue.first().at.isAfter(clock.instant()))return;
                due=queue.pollFirst();byRoom.remove(due.id);}
            try{games.tick(due.id);}catch(RuntimeException error){log.warn("Memory room timer retry: {}",due.id);schedule(due.id,clock.instant().plusSeconds(2));}
        }
    }
    private static final class Due{final Long id;final Instant at;Due(Long id,Instant at){this.id=id;this.at=at;}}
}

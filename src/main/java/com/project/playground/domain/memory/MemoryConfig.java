package com.project.playground.domain.memory;

import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.*;

/** 여러 MVC 인스턴스가 변경 알림·마감 예약을 공유한다. 인증 정책 변경도 기존 스트림에 반영한다. */
@Configuration(proxyBeanMethods=false)
public class MemoryConfig {
    @Bean RedisMessageListenerContainer memoryListener(RedisConnectionFactory factory,MemoryEvents events,MemoryClock clock){
        var listener=new RedisMessageListenerContainer();listener.setConnectionFactory(factory);
        listener.addMessageListener(events,new ChannelTopic(MemoryEvents.TOPIC));
        listener.addMessageListener(events,new ChannelTopic("auth:access-policy:changed:v1"));
        listener.addMessageListener(clock,new ChannelTopic(MemoryEvents.CLOCK_TOPIC));return listener;
    }
}

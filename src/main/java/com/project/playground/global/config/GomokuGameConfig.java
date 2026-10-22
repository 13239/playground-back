package com.project.playground.global.config;

import com.project.playground.domain.gomoku.service.GomokuGameEvents;
import com.project.playground.domain.gomoku.service.GomokuGameClock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import java.time.Clock;

/** 서버 시각을 주입 가능하게 유지하고 여러 MVC 인스턴스에 대국 변경 알림을 전달한다. */
@Configuration(proxyBeanMethods = false)
public class GomokuGameConfig {
    @Bean public Clock gomokuClock() { return Clock.systemUTC(); }
    @Bean public RedisMessageListenerContainer gomokuGameListener(RedisConnectionFactory factory, GomokuGameEvents events, GomokuGameClock clock) {
        var listener = new RedisMessageListenerContainer();
        listener.setConnectionFactory(factory);
        listener.addMessageListener(events, new ChannelTopic(GomokuGameEvents.TOPIC));
        // 마감 예약도 다른 인스턴스에 전달해 요청을 받은 서버에만 시간 처리가 묶이지 않게 한다.
        listener.addMessageListener(clock, new ChannelTopic(GomokuGameEvents.CLOCK_TOPIC));
        return listener;
    }
}

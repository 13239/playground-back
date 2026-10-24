package com.project.playground.global.config;

import com.project.playground.domain.gomoku.service.GomokuGameEvents;
import com.project.playground.domain.gomoku.service.GomokuGameClock;
import com.project.playground.domain.gomoku.service.GomokuLobbyEvents;
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
    @Bean public RedisMessageListenerContainer gomokuGameListener(RedisConnectionFactory factory, GomokuGameEvents events, GomokuGameClock clock, GomokuLobbyEvents lobby) {
        var listener = new RedisMessageListenerContainer();
        listener.setConnectionFactory(factory);
        listener.addMessageListener(events, new ChannelTopic(GomokuGameEvents.TOPIC));
        // 대국 이벤트가 없어도 운영 제한 변경 직후 기존 연결을 다시 인증한다.
        listener.addMessageListener(events, new ChannelTopic("auth:access-policy:changed:v1"));
        // 마감 예약도 다른 인스턴스에 전달해 요청을 받은 서버에만 시간 처리가 묶이지 않게 한다.
        listener.addMessageListener(clock, new ChannelTopic(GomokuGameEvents.CLOCK_TOPIC));
        // 로비 알림은 대국 이벤트와 같은 Redis 연결을 공유하되 구독자 범위는 분리한다.
        listener.addMessageListener(lobby, new ChannelTopic(GomokuLobbyEvents.TOPIC));
        return listener;
    }
}

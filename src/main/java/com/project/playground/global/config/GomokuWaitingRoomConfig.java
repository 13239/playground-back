package com.project.playground.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** MVC 서버의 만료 대기실 정리만 주기 실행하며 브라우저 unload 이벤트에 방 수명을 맡기지 않는다. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class GomokuWaitingRoomConfig { }

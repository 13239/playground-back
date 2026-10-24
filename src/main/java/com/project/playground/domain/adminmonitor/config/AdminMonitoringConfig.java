package com.project.playground.domain.adminmonitor.config;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** 집계·상태 확인의 I/O가 기존 대국 시계와 정리 작업의 기본 스케줄러를 점유하지 않게 한다. */
@Configuration(proxyBeanMethods = false)
public class AdminMonitoringConfig {
    @Bean(defaultCandidate = false)
    public ThreadPoolTaskScheduler adminMonitoringScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2); scheduler.setThreadNamePrefix("admin-monitor-"); scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false); scheduler.setAwaitTerminationSeconds(5);
        return scheduler;
    }
}

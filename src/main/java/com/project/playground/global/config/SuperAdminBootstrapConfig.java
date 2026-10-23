package com.project.playground.global.config;

import com.project.playground.domain.authorization.bootstrap.SuperAdminBootstrapProperties;
import com.project.playground.domain.authorization.bootstrap.SuperAdminBootstrapService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 기본값은 꺼짐이며 admin 계정 초기 배정은 지정한 실행에서만 수행한다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SuperAdminBootstrapProperties.class)
public class SuperAdminBootstrapConfig {
    @Bean
    @ConditionalOnProperty(prefix = "authorization.bootstrap", name = "enabled", havingValue = "true")
    ApplicationRunner initializeSuperAdmin(SuperAdminBootstrapService service, SuperAdminBootstrapProperties properties) {
        return args -> service.initialize(properties.getUsername());
    }
}


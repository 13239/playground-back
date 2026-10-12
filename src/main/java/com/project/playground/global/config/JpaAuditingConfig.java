package com.project.playground.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(modifyOnCreate = false) // modifyOnCreate = false 옵션을 주면 LastModifiedDate를 생성하지 않는다.
public class JpaAuditingConfig {
}

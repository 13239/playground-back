package com.project.playground.domain.authorization.bootstrap;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** 일반 시작은 초기 배정을 하지 않으며 운영자가 한 번 명시적으로 활성화한다. */
@Getter
@Validated
@ConfigurationProperties("authorization.bootstrap")
public class SuperAdminBootstrapProperties {
    private final boolean enabled;
    @NotBlank @Size(max = 50)
    private final String username;

    public SuperAdminBootstrapProperties(@DefaultValue("false") boolean enabled, @DefaultValue("admin") String username) {
        this.enabled = enabled;
        this.username = username;
    }
}


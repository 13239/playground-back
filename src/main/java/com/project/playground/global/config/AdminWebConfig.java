package com.project.playground.global.config;

import com.project.playground.global.security.admin.AdminPermissionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 경로 별칭으로 기능 검사를 우회하지 않도록 관리자 선언 검사는 전체 MVC 매핑에 적용한다. */
@Configuration(proxyBeanMethods = false)
public class AdminWebConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminPermissionInterceptor());
    }
}


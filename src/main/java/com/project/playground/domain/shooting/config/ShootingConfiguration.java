package com.project.playground.domain.shooting.config;

import com.project.playground.domain.shooting.security.ShootingServiceAuthFilter;
import com.project.playground.global.security.handler.SecurityProblemWriter;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/** 게임 전용 내부 API에만 HMAC 인증을 적용하고 기존 BFF·JWT·브라우저 인증 체인은 변경하지 않는다. */
@Configuration
@PropertySource("classpath:shooting.properties")
@EnableConfigurationProperties(ShootingProperties.class)
public class ShootingConfiguration {
    @Bean
    @Order(1)
    public SecurityFilterChain shootingInternalSecurity(
            HttpSecurity http,
            ShootingProperties properties,
            StringRedisTemplate redis,
            SecurityProblemWriter errors)
            throws Exception {
        return http.securityMatcher("/internal/shooting/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(
                        new ShootingServiceAuthFilter(properties, redis, errors),
                        AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
    }
}

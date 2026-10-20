package com.project.playground.global.config;

import com.project.playground.auth.ratelimit.LoginRateLimitProperties;
import com.project.playground.auth.clientip.LoginClientIpResolver;
import com.project.playground.auth.proof.BrowserProofFilter;
import com.project.playground.auth.proof.BrowserProofService;
import com.project.playground.global.security.BffClientFilter;
import com.project.playground.global.security.handler.SecurityProblemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import com.project.playground.global.security.handler.ApiAccessDeniedHandler;
import com.project.playground.global.security.handler.ApiAuthenticationEntryPoint;
import com.project.playground.global.security.jwt.JwtAuthenticationConverter;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableConfigurationProperties(LoginRateLimitProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    // 보호된 경로의 미인증 요청과 Bearer 인증 실패에 사용할 공통 처리기
    private final ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;

    // 인증된 사용자의 접근 권한 부족에 사용할 공통 처리기
    private final ApiAccessDeniedHandler apiAccessDeniedHandler;

    private final BrowserProofService browserProofService;

    private final LoginClientIpResolver loginClientIpResolver;

    private final SecurityProblemWriter securityProblemWriter;

    private final Environment environment;

    @Value("${auth.bff.mtls-required:true}")
    private boolean mtlsRequired;

    @Value("${auth.bff.allowed-cert-sha256:}")
    private String allowedCertificates;

    // Docker 내부 HTTP를 사용하는 dev에서만 필요한 서버 전용 인증 키다.
    @Value("${auth.bff.dev-shared-secret:}")
    private String devSharedSecret;

    @Value("${auth.proof.required:true}")
    private boolean proofRequired;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // 개인키 검증 생략은 dev에서만 허용한다. prod/local을 함께 지정한 경우는 BFF 필터도 거절한다.
        if (!proofRequired && !environment.matchesProfiles("dev")) {
            throw new IllegalArgumentException("브라우저 개인키 검증은 dev HTTP에서만 생략할 수 있습니다.");
        }
        // 운영에서 브라우저 증명을 HTTP Origin으로 잘못 묶은 설정도 시작 시점에 거절한다.
        if (mtlsRequired && !environment.getProperty("auth.proof.origin", "").startsWith("https://")) {
            throw new IllegalArgumentException("운영 브라우저 Origin은 HTTPS여야 합니다.");
        }
        return http
                // 서버 인증은 공개 로그인·nonce에도 적용하고 브라우저 증명은 Bearer 인증 뒤에 확인한다.
                .addFilterBefore(new BffClientFilter(mtlsRequired, allowedCertificates, devSharedSecret, environment, securityProblemWriter), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new BrowserProofFilter(browserProofService, loginClientIpResolver, securityProblemWriter, proofRequired), BearerTokenAuthenticationFilter.class)
                .cors(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .rememberMe(AbstractHttpConfigurer::disable)
                .requestCache(RequestCacheConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(apiAuthenticationEntryPoint)
                        .accessDeniedHandler(apiAccessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/user/sign-up",
                                "/api/auth/login",
                                "/api/auth/proof-challenge",
                                "/api/auth/refresh"
                        ).permitAll()
                        .requestMatchers("/api/test").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(apiAuthenticationEntryPoint)
                        .accessDeniedHandler(apiAccessDeniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                            @Override
                            public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                                // 기본 처리기의 서비스 예외 재전파를 대신하여 Redis 장애도 공통 503으로 응답한다.
                                filter.setAuthenticationFailureHandler(apiAuthenticationEntryPoint::commence);
                                return filter;
                            }
                        })
                )
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(provider);
    }
}

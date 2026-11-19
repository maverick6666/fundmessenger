package com.fundmessenger.config;

import com.fundmessenger.auth.filter.JwtAuthFilter;
import com.fundmessenger.auth.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtService jwtService;
    private final RateLimitFilter rateLimitFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CORS 활성화 — CorsConfigurationSource 빈 자동 감지
            .cors(Customizer.withDefaults())

            // CSRF 비활성화 (JWT 사용)
            .csrf(csrf -> csrf.disable())

            // 보안 헤더 설정
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())  // SAMEORIGIN (클릭재킹 방지, PDF iframe 허용)
                .contentTypeOptions(Customizer.withDefaults())  // X-Content-Type-Options: nosniff
                .referrerPolicy(referrer -> referrer
                    .policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            )

            // 세션 사용 안함 (Stateless)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // URL 별 접근 권한
            .authorizeHttpRequests(auth -> auth
                // SEC-11: 인증 관련 — OAuth + dev-login만 허용 (/me, /me/profile은 인증 필요)
                .requestMatchers(
                    "/api/auth/oauth2/**",
                    "/api/auth/dev-login",
                    "/api/auth/dev-reset/**",
                    "/api/onboarding/schools",
                    "/api/onboarding/schools/*/clubs",
                    "/api/files/images/**",
                    "/api/files/pdfs/**",
                    "/uploads/**",
                    "/ws/**",
                    "/api/push/vapid-public-key"
                ).permitAll()
                // 나머지는 인증 필요
                .anyRequest().authenticated()
            )

            // 미인증 시 401 반환 (기본 403 대신)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )

            // JWT 필터 추가 (UsernamePasswordAuth 앞)
            .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class)

            // Rate Limit 필터 (JWT 앞 — 가장 먼저 실행)
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

}

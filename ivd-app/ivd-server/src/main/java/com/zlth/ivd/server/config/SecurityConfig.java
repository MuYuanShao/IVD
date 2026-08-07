package com.zlth.ivd.server.config;

import com.zlth.ivd.common.utils.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 安全配置
 * <p>
 * 一期采用 JWT 无状态认证 + RBAC 权限控制
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CORS 配置（Vue 前端开发服务器跨域访问）
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // 禁用 CSRF（JWT 无状态）
            .csrf(AbstractHttpConfigurer::disable)
            // 无状态会话
            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 请求权限
            .authorizeHttpRequests(auth -> auth
                    // 公开接口
                    .requestMatchers(
                            "/api/v1/auth/login",
                            "/api/v1/auth/refresh",
                            "/v3/api-docs/**",
                            "/swagger-ui/**",
                            "/swagger-ui.html",
                            "/actuator/health",
                            "/actuator/info",
                            "/error"
                    ).permitAll()
                    // 证照附件预览/下载 — 仅 GET 公开，DELETE/PUT 需认证
                    .requestMatchers(HttpMethod.GET, "/api/v1/license/*/file/**").permitAll()
                    // 审计接口需认证（人工决策回写涉及敏感操作）
                    .requestMatchers("/api/v1/audit/**").authenticated()
                    // 其他接口均需认证
                    .anyRequest().authenticated()
            )
            // JWT 认证过滤器（在 UsernamePasswordAuthenticationFilter 之前）
            .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                    UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS 跨域配置
     * <p>
     * 允许前端 Vite dev server（默认 5173）及生产构建同源访问。
     * 生产环境应通过反向代理统一域名，此配置主要服务于本地开发联调。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

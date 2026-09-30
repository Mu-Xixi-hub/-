package com.example.login;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean UserDetailsService userDetailsService(UserRepository users) {
        return username -> users.findByUsername(username)
            .map(u -> User.withUsername(u.username()).password(u.passwordHash()).roles("USER").build())
            .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
    }

    @Bean CommandLineRunner demoUsers(UserRepository users, PasswordEncoder encoder) {
        return args -> {
            users.createIfAbsent("zhangsan", encoder.encode("Demo123!"), "张三", "zhangsan@example.com", "研发部");
            users.createIfAbsent("lisi", encoder.encode("Demo123!"), "李四", "lisi@example.com", "产品部");
        };
    }

    @Bean CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of("http://localhost:5500"));
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN"));
        cors.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // 保留默认 CSRF 防护；Spring Security 负责验证密码、变更会话 ID 和保存登录身份。
        return http.cors(cors -> {})
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/csrf", "/api/login").permitAll()
                .anyRequest().authenticated())
            .requestCache(cache -> cache.disable())
            .formLogin(form -> form.loginProcessingUrl("/api/login")
                .successHandler((request, response, authentication) -> json(response, 200, "登录成功"))
                .failureHandler((request, response, exception) -> json(response, 401, "用户名或密码错误")))
            .logout(logout -> logout.logoutUrl("/api/logout")
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> json(response, 200, "已退出登录")))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> json(response, 401, "请先登录"))
                .accessDeniedHandler((request, response, exception) -> json(response, 403, "请求验证失败，请刷新页面后重试")))
            .build();
    }

    private static void json(HttpServletResponse response, int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}

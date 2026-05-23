package com.jupin.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeRequests()
            .antMatchers(
                    "/api/auth/register", "/api/auth/login", "/api/auth/refresh",
                    "/api/player/pool/list", "/api/player/pool/*", "/api/player/script/list",
                    "/api/shop/search", "/api/shop/script/list",
                    "/api/admin/script/list",
                    "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/ws/**"
            ).permitAll()
            .antMatchers("/api/player/**", "/api/shop/**", "/api/admin/**").permitAll()
            .anyRequest().denyAll()
            .and()
            .headers().frameOptions().disable();
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

package com.shareride.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Reactive Spring Security configuration for API Gateway perimeter.
 *
 * <p>Disables default CSRF, HTTP Basic, and form login so the reactive Gateway can act as a
 * stateless perimeter proxy, delegating authentication to {@link
 * com.shareride.gateway.filter.AuthenticationFilter}.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http.csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .logout(logout -> logout.disable())
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
    }
}

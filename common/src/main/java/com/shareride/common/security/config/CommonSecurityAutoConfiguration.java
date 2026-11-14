package com.shareride.common.security.config;

import com.shareride.common.security.constant.CommonSecurityConstants;
import com.shareride.common.security.filter.SecurityContextBridgeFilter;
import com.shareride.common.security.handler.RestAccessDeniedHandler;
import com.shareride.common.security.handler.RestAuthenticationEntryPoint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Common Security Auto-Configuration for stateless REST microservices.
 *
 * <p>Provides a reusable Spring Security configuration with:
 *
 * <ul>
 *   <li>Stateless session management (no HTTP sessions, CSRF, or form login)
 *   <li>Method-level authorization via {@code @PreAuthorize} annotations
 *   <li>Custom REST error handlers for 401 and 403 responses
 *   <li>Security context bridge from {@code UserContextHolder} to {@code SecurityContextHolder}
 *   <li>Configurable public endpoint whitelist via {@link SecurityProperties}
 * </ul>
 *
 * <p>This configuration is automatically applied to all microservices importing the {@code common}
 * module via {@code
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 *
 * <p>Services can configure additional public endpoints in {@code application.yml}:
 *
 * <pre>
 * shareride:
 *   security:
 *     public-endpoints:
 *       - /auth/v1/login
 *       - /auth/v1/register
 *       - /auth/v1/refresh
 *       - /auth/v1/public/**
 * </pre>
 */
@AutoConfiguration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties(SecurityProperties.class)
public class CommonSecurityAutoConfiguration {

    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final SecurityProperties securityProperties;

    public CommonSecurityAutoConfiguration(
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            SecurityProperties securityProperties) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.securityProperties = securityProperties;
    }

    /**
     * Configures the stateless security filter chain for REST APIs.
     *
     * @param http the {@link HttpSecurity} builder
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        List<String> allPublicEndpoints = new ArrayList<>();
        allPublicEndpoints.addAll(Arrays.asList(CommonSecurityConstants.PUBLIC_ENDPOINTS));
        if (securityProperties.getPublicEndpoints() != null) {
            allPublicEndpoints.addAll(securityProperties.getPublicEndpoints());
        }

        String[] publicEndpointsArray = allPublicEndpoints.toArray(new String[0]);

        http.csrf(csrf -> csrf.disable())
                .formLogin(formLogin -> formLogin.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        authorize ->
                                authorize
                                        .requestMatchers(publicEndpointsArray)
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(authenticationEntryPoint)
                                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterAfter(
                        new SecurityContextBridgeFilter(),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

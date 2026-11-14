package com.shareride.common.security.filter;

import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.security.constant.CommonSecurityConstants;
import com.shareride.common.security.principal.SecurityUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bridges {@link UserContextHolder} authenticated state into Spring Security's {@link
 * SecurityContextHolder}.
 *
 * <p>This filter runs after {@code UserContextFilter} in the filter chain and extracts the
 * authenticated user from {@code UserContextHolder}, constructs a {@link SecurityUserPrincipal},
 * maps roles and permissions to {@link GrantedAuthority} with dual prefixing (e.g., ADMIN and
 * ROLE_ADMIN), and populates {@code SecurityContextHolder} for downstream {@code @PreAuthorize}
 * evaluation.
 *
 * <p>The security context is cleared in the {@code finally} block to prevent thread pool
 * contamination in asynchronous or pooled execution environments.
 */
public class SecurityContextBridgeFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            UserContext userContext = UserContextHolder.getContext();

            if (userContext != null && userContext.isAuthenticated()) {
                SecurityUserPrincipal principal =
                        SecurityUserPrincipal.builder()
                                .userId(userContext.getUserId())
                                .email(userContext.getEmail())
                                .tenantId(userContext.getTenantId())
                                .sessionId(userContext.getSessionId())
                                .roles(userContext.getRoles())
                                .permissions(userContext.getPermissions())
                                .build();

                Set<GrantedAuthority> authorities = mapAuthorities(userContext);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Maps roles and permissions to Spring Security {@link GrantedAuthority} with dual prefixing.
     *
     * <p>/** Maps roles and permissions to Spring Security {@link GrantedAuthority} with dual
     * prefixing.
     *
     * <p>For each role (e.g., ADMIN or ROLE_ADMIN), strips any existing ROLE_ prefix, then
     * generates both ROLE_ADMIN and ADMIN authorities. For each permission (e.g., account:read or
     * PERM_account:read), strips any existing PERM_ prefix, then generates both PERM_account:read
     * and account:read authorities. This normalization ensures {@code hasRole('ADMIN')} and {@code
     * hasAuthority('ADMIN')} both work seamlessly.
     *
     * @param userContext the authenticated user context
     * @return a set of granted authorities with dual prefixing
     */
    private Set<GrantedAuthority> mapAuthorities(UserContext userContext) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        if (userContext.getRoles() != null) {
            for (String role : userContext.getRoles()) {
                if (role == null || role.isBlank()) {
                    continue;
                }
                String normalizedRole = role.trim();
                if (normalizedRole
                        .toUpperCase(Locale.ROOT)
                        .startsWith(CommonSecurityConstants.AUTHORITY_ROLE_PREFIX)) {
                    normalizedRole =
                            normalizedRole.substring(
                                    CommonSecurityConstants.AUTHORITY_ROLE_PREFIX.length());
                }
                String upperRole = normalizedRole.toUpperCase(Locale.ROOT);
                authorities.add(
                        new SimpleGrantedAuthority(
                                CommonSecurityConstants.AUTHORITY_ROLE_PREFIX + upperRole));
                authorities.add(new SimpleGrantedAuthority(upperRole));
                if (!upperRole.equals(normalizedRole)) {
                    authorities.add(
                            new SimpleGrantedAuthority(
                                    CommonSecurityConstants.AUTHORITY_ROLE_PREFIX
                                            + normalizedRole));
                    authorities.add(new SimpleGrantedAuthority(normalizedRole));
                }
            }
        }

        if (userContext.getPermissions() != null) {
            for (String permission : userContext.getPermissions()) {
                if (permission == null || permission.isBlank()) {
                    continue;
                }
                String normalizedPermission = permission.trim();
                if (normalizedPermission
                        .toUpperCase(Locale.ROOT)
                        .startsWith(CommonSecurityConstants.AUTHORITY_PERM_PREFIX)) {
                    normalizedPermission =
                            normalizedPermission.substring(
                                    CommonSecurityConstants.AUTHORITY_PERM_PREFIX.length());
                }
                authorities.add(
                        new SimpleGrantedAuthority(
                                CommonSecurityConstants.AUTHORITY_PERM_PREFIX
                                        + normalizedPermission));
                authorities.add(new SimpleGrantedAuthority(normalizedPermission));
            }
        }

        return authorities;
    }
}

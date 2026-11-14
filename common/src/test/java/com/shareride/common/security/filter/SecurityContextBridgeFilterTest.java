package com.shareride.common.security.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link SecurityContextBridgeFilter}.
 *
 * <p>Verifies context propagation from {@link UserContextHolder} to {@link SecurityContextHolder}
 * and ThreadLocal cleanup without booting full Spring context.
 */
@ExtendWith(MockitoExtension.class)
class SecurityContextBridgeFilterTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    private SecurityContextBridgeFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SecurityContextBridgeFilter();
        SecurityContextHolder.clearContext();
        UserContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        UserContextHolder.clear();
    }

    @Test
    void doFilterInternal_withAuthenticatedUser_shouldPopulateAndClearSecurityContext()
            throws Exception {
        UserContext userContext =
                UserContext.builder()
                        .userId("018d9f1e-8742-7ef1-95c2-f123456789ab")
                        .email("admin@shareride.com")
                        .tenantId("default")
                        .sessionId("018d9f1e-8742-7ef1-95c2-a987654321ba")
                        .roles(Set.of("ADMIN", "RIDER"))
                        .permissions(Set.of("account:read", "account:write"))
                        .build();

        UserContextHolder.setContext(userContext);

        AtomicReference<Authentication> capturedAuth = new AtomicReference<>();
        doAnswer(
                        invocation -> {
                            capturedAuth.set(
                                    SecurityContextHolder.getContext().getAuthentication());
                            return null;
                        })
                .when(filterChain)
                .doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);

        assertThat(capturedAuth.get()).isNotNull();
        assertThat(capturedAuth.get().getAuthorities())
                .extracting(auth -> auth.getAuthority())
                .contains(
                        "ROLE_ADMIN",
                        "ADMIN",
                        "ROLE_RIDER",
                        "RIDER",
                        "PERM_account:read",
                        "account:read");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();
    }

    @Test
    void doFilterInternal_withLowercaseRoles_shouldNormalizeToUppercaseAuthorities()
            throws Exception {
        UserContext userContext =
                UserContext.builder()
                        .userId("018d9f1e-8742-7ef1-95c2-f123456789ab")
                        .email("admin@shareride.com")
                        .roles(Set.of("admin", "role_rider"))
                        .permissions(Set.of("account:read"))
                        .build();

        UserContextHolder.setContext(userContext);

        AtomicReference<Authentication> capturedAuth = new AtomicReference<>();
        doAnswer(
                        invocation -> {
                            capturedAuth.set(
                                    SecurityContextHolder.getContext().getAuthentication());
                            return null;
                        })
                .when(filterChain)
                .doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);

        assertThat(capturedAuth.get()).isNotNull();
        assertThat(capturedAuth.get().getAuthorities())
                .extracting(auth -> auth.getAuthority())
                .contains("ROLE_ADMIN", "ADMIN", "ROLE_RIDER", "RIDER");
    }

    @Test
    void doFilterInternal_withUnauthenticatedUser_shouldNotPopulateSecurityContext()
            throws Exception {
        UserContext userContext = UserContext.builder().email("user@shareride.com").build();

        UserContextHolder.setContext(userContext);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();
    }

    @Test
    void doFilterInternal_withNullUserContext_shouldNotPopulateSecurityContext() throws Exception {
        UserContextHolder.clear();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();
    }

    @Test
    void doFilterInternal_shouldClearSecurityContextInFinallyBlock() throws Exception {
        UserContext userContext =
                UserContext.builder()
                        .userId("018d9f1e-8742-7ef1-95c2-f123456789ab")
                        .email("admin@shareride.com")
                        .roles(Set.of("ADMIN"))
                        .build();

        UserContextHolder.setContext(userContext);

        filter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();
    }
}

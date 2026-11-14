package com.shareride.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareride.common.context.UserContext;
import com.shareride.common.security.filter.SecurityContextBridgeFilter;
import jakarta.servlet.FilterChain;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Tests for authority normalization in {@link SecurityContextBridgeFilter}.
 *
 * <p>Validates that roles and permissions are correctly normalized with dual prefixing, ensuring
 * both {@code ADMIN} and {@code ROLE_ADMIN} work interchangeably, and both {@code account:read} and
 * {@code PERM_account:read} are supported.
 */
@ExtendWith(MockitoExtension.class)
class AuthorityNormalizationTest {

    private final SecurityContextBridgeFilter filter = new SecurityContextBridgeFilter();

    @AfterEach
    void tearDown() {
        com.shareride.common.context.UserContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminRole_generatesNormalizedAuthorities() throws Exception {
        UserContext userContext =
                UserContext.builder()
                        .userId("018d9f1e-8742-7ef1-95c2-a987654321ba")
                        .email("admin@shareride.com")
                        .roles(Set.of("ADMIN"))
                        .build();

        com.shareride.common.context.UserContextHolder.setContext(userContext);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Capture authentication during filter execution
        final Authentication[] capturedAuth = new Authentication[1];
        FilterChain chain =
                (req, res) -> {
                    capturedAuth[0] = SecurityContextHolder.getContext().getAuthentication();
                };

        filter.doFilter(request, response, chain);

        assertThat(capturedAuth[0]).isNotNull();
        Set<String> authorities =
                capturedAuth[0].getAuthorities().stream()
                        .map(auth -> auth.getAuthority())
                        .collect(java.util.stream.Collectors.toSet());

        assertThat(authorities).contains("ADMIN", "ROLE_ADMIN");
    }

    @Test
    void roleAdminPrefix_stripsAndNormalizesCorrectly() throws Exception {
        UserContext userContext =
                UserContext.builder()
                        .userId("018d9f1e-8742-7ef1-95c2-a987654321ba")
                        .email("admin@shareride.com")
                        .roles(Set.of("ROLE_ADMIN"))
                        .build();

        com.shareride.common.context.UserContextHolder.setContext(userContext);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        final Authentication[] capturedAuth = new Authentication[1];
        FilterChain chain =
                (req, res) -> {
                    capturedAuth[0] = SecurityContextHolder.getContext().getAuthentication();
                };

        filter.doFilter(request, response, chain);

        assertThat(capturedAuth[0]).isNotNull();
        Set<String> authorities =
                capturedAuth[0].getAuthorities().stream()
                        .map(auth -> auth.getAuthority())
                        .collect(java.util.stream.Collectors.toSet());

        assertThat(authorities).contains("ADMIN", "ROLE_ADMIN");
    }

    @Test
    void permission_generatesNormalizedAuthorities() throws Exception {
        UserContext userContext =
                UserContext.builder()
                        .userId("018d9f1e-8742-7ef1-95c2-f123456789ab")
                        .email("user@shareride.com")
                        .permissions(Set.of("account:read"))
                        .build();

        com.shareride.common.context.UserContextHolder.setContext(userContext);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        final Authentication[] capturedAuth = new Authentication[1];
        FilterChain chain =
                (req, res) -> {
                    capturedAuth[0] = SecurityContextHolder.getContext().getAuthentication();
                };

        filter.doFilter(request, response, chain);

        assertThat(capturedAuth[0]).isNotNull();
        Set<String> authorities =
                capturedAuth[0].getAuthorities().stream()
                        .map(auth -> auth.getAuthority())
                        .collect(java.util.stream.Collectors.toSet());

        assertThat(authorities).contains("account:read", "PERM_account:read");
    }

    @Test
    void permissionWithPrefix_stripsAndNormalizesCorrectly() throws Exception {
        UserContext userContext =
                UserContext.builder()
                        .userId("018d9f1e-8742-7ef1-95c2-f123456789ab")
                        .email("user@shareride.com")
                        .permissions(Set.of("PERM_account:write"))
                        .build();

        com.shareride.common.context.UserContextHolder.setContext(userContext);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        final Authentication[] capturedAuth = new Authentication[1];
        FilterChain chain =
                (req, res) -> {
                    capturedAuth[0] = SecurityContextHolder.getContext().getAuthentication();
                };

        filter.doFilter(request, response, chain);

        assertThat(capturedAuth[0]).isNotNull();
        Set<String> authorities =
                capturedAuth[0].getAuthorities().stream()
                        .map(auth -> auth.getAuthority())
                        .collect(java.util.stream.Collectors.toSet());

        assertThat(authorities).contains("account:write", "PERM_account:write");
    }

    @Test
    void mixedRolesAndPermissions_normalizeCorrectly() throws Exception {
        UserContext userContext =
                UserContext.builder()
                        .userId("018d9f1e-8742-7ef1-95c2-f123456789ab")
                        .email("user@shareride.com")
                        .roles(Set.of("RIDER", "ROLE_DRIVER"))
                        .permissions(Set.of("account:read", "PERM_trip:create"))
                        .build();

        com.shareride.common.context.UserContextHolder.setContext(userContext);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        final Authentication[] capturedAuth = new Authentication[1];
        FilterChain chain =
                (req, res) -> {
                    capturedAuth[0] = SecurityContextHolder.getContext().getAuthentication();
                };

        filter.doFilter(request, response, chain);

        assertThat(capturedAuth[0]).isNotNull();
        Set<String> authorities =
                capturedAuth[0].getAuthorities().stream()
                        .map(auth -> auth.getAuthority())
                        .collect(java.util.stream.Collectors.toSet());

        assertThat(authorities)
                .contains(
                        "RIDER",
                        "ROLE_RIDER",
                        "DRIVER",
                        "ROLE_DRIVER",
                        "account:read",
                        "PERM_account:read",
                        "trip:create",
                        "PERM_trip:create");
    }
}

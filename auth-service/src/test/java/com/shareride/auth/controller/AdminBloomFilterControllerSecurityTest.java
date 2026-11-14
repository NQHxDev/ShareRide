package com.shareride.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Security annotation verification tests for {@link AdminBloomFilterController}.
 *
 * <p>Verifies that {@code @PreAuthorize("hasRole('ADMIN')")} annotations are correctly applied to
 * administrative endpoints. Actual security behavior (401, 403, 200 responses) is verified in
 * {@link com.shareride.auth.integration.AdminBloomFilterSecurityIntegrationTest}.
 *
 * <p>This lightweight test adheres to Constitution Principle V (pragmatic testing strategy) by
 * verifying annotation presence here and delegating behavior verification to integration tests.
 */
class AdminBloomFilterControllerSecurityTest {

    @Test
    @DisplayName("AdminBloomFilterController class should have @PreAuthorize annotation")
    void controllerClass_shouldHavePreAuthorizeAnnotation() {
        PreAuthorize annotation =
                AdminBloomFilterController.class.getAnnotation(PreAuthorize.class);
        assertThat(annotation)
                .as(
                        "AdminBloomFilterController should have @PreAuthorize annotation at class"
                                + " level")
                .isNotNull();
        assertThat(annotation.value())
                .as("@PreAuthorize should require ADMIN role")
                .isEqualTo("hasRole('ADMIN')");
    }

    @Test
    @DisplayName("rebuildBloomFilter method should have @PreAuthorize annotation")
    void rebuildBloomFilterMethod_shouldHavePreAuthorizeAnnotation() throws NoSuchMethodException {
        Method method = AdminBloomFilterController.class.getMethod("rebuildBloomFilter");
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation)
                .as("rebuildBloomFilter method should have @PreAuthorize annotation")
                .isNotNull();
        assertThat(annotation.value())
                .as("@PreAuthorize should require ADMIN role")
                .isEqualTo("hasRole('ADMIN')");
    }

    @Test
    @DisplayName("initializeBloomFilter method should have @PreAuthorize annotation")
    void initializeBloomFilterMethod_shouldHavePreAuthorizeAnnotation()
            throws NoSuchMethodException {
        Method method = AdminBloomFilterController.class.getMethod("initializeBloomFilter");
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation)
                .as("initializeBloomFilter method should have @PreAuthorize annotation")
                .isNotNull();
        assertThat(annotation.value())
                .as("@PreAuthorize should require ADMIN role")
                .isEqualTo("hasRole('ADMIN')");
    }
}

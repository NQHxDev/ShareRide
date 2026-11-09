package com.shareride.common.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShareRideAuditorAwareTest {

    private final ShareRideAuditorAware auditorAware = new ShareRideAuditorAware();

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    @DisplayName("Should return SYSTEM when UserContext is empty")
    void testFallbackToSystem() {
        UserContextHolder.clear();
        Optional<String> auditor = auditorAware.getCurrentAuditor();
        assertTrue(auditor.isPresent());
        assertEquals(ShareRideAuditorAware.SYSTEM_USER, auditor.get());
    }

    @Test
    @DisplayName("Should return userId when UserContext is present")
    void testAuditorFromContext() {
        UserContextHolder.setContext(UserContext.builder().userId("driver-999").build());
        Optional<String> auditor = auditorAware.getCurrentAuditor();
        assertTrue(auditor.isPresent());
        assertEquals("driver-999", auditor.get());
    }
}

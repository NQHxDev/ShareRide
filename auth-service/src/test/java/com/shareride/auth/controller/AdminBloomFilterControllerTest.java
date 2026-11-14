package com.shareride.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.auth.service.EmailBloomFilterService;
import com.shareride.common.response.BaseResponse;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminBloomFilterControllerTest {

    @Mock private EmailBloomFilterService emailBloomFilterService;

    private AdminBloomFilterController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminBloomFilterController(emailBloomFilterService);
    }

    @Test
    @DisplayName("rebuildBloomFilter should return 200 OK with emails added on success")
    void testRebuildBloomFilter_success() {
        when(emailBloomFilterService.rebuildFromDatabase()).thenReturn(42L);

        ResponseEntity<BaseResponse<Map<String, Long>>> response = controller.rebuildBloomFilter();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(42L, response.getBody().getData().get("emailsAdded"));
        verify(emailBloomFilterService).rebuildFromDatabase();
    }

    @Test
    @DisplayName(
            "rebuildBloomFilter should return 409 Conflict when rebuild is already in progress")
    void testRebuildBloomFilter_alreadyInProgress() {
        AtomicBoolean rebuildInProgress =
                (AtomicBoolean) ReflectionTestUtils.getField(controller, "rebuildInProgress");
        assertNotNull(rebuildInProgress);
        rebuildInProgress.set(true);

        ResponseEntity<BaseResponse<Map<String, Long>>> response = controller.rebuildBloomFilter();

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("BLOOM_FILTER_REBUILD_IN_PROGRESS", response.getBody().getCode());
    }

    @Test
    @DisplayName("rebuildBloomFilter should return 500 when rebuild throws an exception")
    void testRebuildBloomFilter_exception() {
        when(emailBloomFilterService.rebuildFromDatabase())
                .thenThrow(new RuntimeException("Database error"));

        ResponseEntity<BaseResponse<Map<String, Long>>> response = controller.rebuildBloomFilter();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("BLOOM_FILTER_REBUILD_FAILED", response.getBody().getCode());
    }

    @Test
    @DisplayName(
            "initializeBloomFilter should return 200 OK with alreadyExisted=true when already"
                    + " initialized")
    void testInitializeBloomFilter_alreadyInitialized() {
        when(emailBloomFilterService.isInitialized()).thenReturn(true);

        ResponseEntity<BaseResponse<Map<String, Boolean>>> response =
                controller.initializeBloomFilter();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(Boolean.TRUE, response.getBody().getData().get("initialized"));
        assertEquals(Boolean.TRUE, response.getBody().getData().get("alreadyExisted"));
    }

    @Test
    @DisplayName(
            "initializeBloomFilter should initialize and return 200 with alreadyExisted=false when"
                    + " not yet initialized")
    void testInitializeBloomFilter_notYetInitialized() {
        when(emailBloomFilterService.isInitialized()).thenReturn(false);

        ResponseEntity<BaseResponse<Map<String, Boolean>>> response =
                controller.initializeBloomFilter();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(Boolean.TRUE, response.getBody().getData().get("initialized"));
        assertEquals(Boolean.FALSE, response.getBody().getData().get("alreadyExisted"));
        verify(emailBloomFilterService).initializeBloomFilter();
    }

    @Test
    @DisplayName("initializeBloomFilter should return 500 when service throws an exception")
    void testInitializeBloomFilter_exception() {
        when(emailBloomFilterService.isInitialized()).thenReturn(false);
        doThrow(new RuntimeException("Redis error"))
                .when(emailBloomFilterService)
                .initializeBloomFilter();

        ResponseEntity<BaseResponse<Map<String, Boolean>>> response =
                controller.initializeBloomFilter();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("BLOOM_FILTER_INIT_FAILED", response.getBody().getCode());
    }
}

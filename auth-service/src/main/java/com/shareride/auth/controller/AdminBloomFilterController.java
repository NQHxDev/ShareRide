package com.shareride.auth.controller;

import com.shareride.auth.service.EmailBloomFilterService;
import com.shareride.common.response.BaseResponse;
import com.shareride.common.versioning.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin controller for Bloom filter management operations. Provides endpoints for rebuild and
 * initialization. TODO: Enable @PreAuthorize("hasRole('ADMIN')") when Spring Security is fully
 * configured
 */
@Slf4j
@RestController
@RequestMapping("/auth/v1/admin/bloom-filter")
@ApiVersion("v1")
@RequiredArgsConstructor
@Tag(name = "Admin - Bloom Filter", description = "Quản lý Bloom filter cho email")
public class AdminBloomFilterController {

    private final EmailBloomFilterService emailBloomFilterService;

    /**
     * Rebuilds Bloom filter from PostgreSQL account data. This operation clears existing filter and
     * repopulates from database.
     *
     * @return number of emails added to rebuilt filter
     */
    @PostMapping("/rebuild")
    // @PreAuthorize("hasRole('ADMIN')") // TODO: Enable when Spring Security configured
    @Operation(
            summary = "Xây dựng lại Bloom filter từ database",
            description = "Xóa Bloom filter hiện tại và xây dựng lại từ dữ liệu PostgreSQL",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<BaseResponse<Map<String, Long>>> rebuildBloomFilter() {
        log.info("Admin rebuild Bloom filter request received");

        try {
            long emailsAdded = emailBloomFilterService.rebuildFromDatabase();

            return ResponseEntity.ok(
                    BaseResponse.success(
                            Map.of("emailsAdded", emailsAdded),
                            "Bloom filter đã được xây dựng lại thành công"));

        } catch (Exception e) {
            log.error("Bloom filter rebuild failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                            BaseResponse.error(
                                    "BLOOM_FILTER_REBUILD_FAILED",
                                    "Không thể xây dựng lại Bloom filter: " + e.getMessage()));
        }
    }

    /**
     * Manually initializes Bloom filter. Useful for first-time setup or recovery.
     *
     * @return initialization status
     */
    @PostMapping("/initialize")
    // @PreAuthorize("hasRole('ADMIN')") // TODO: Enable when Spring Security configured
    @Operation(
            summary = "Khởi tạo Bloom filter",
            description = "Khởi tạo Bloom filter nếu chưa tồn tại",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<BaseResponse<Map<String, Boolean>>> initializeBloomFilter() {
        log.info("Admin initialize Bloom filter request received");

        try {
            boolean alreadyInitialized = emailBloomFilterService.isInitialized();

            if (alreadyInitialized) {
                return ResponseEntity.ok(
                        BaseResponse.success(
                                Map.of("initialized", true, "alreadyExisted", true),
                                "Bloom filter đã được khởi tạo trước đó"));
            }

            emailBloomFilterService.initializeBloomFilter();

            return ResponseEntity.ok(
                    BaseResponse.success(
                            Map.of("initialized", true, "alreadyExisted", false),
                            "Bloom filter đã được khởi tạo thành công"));

        } catch (Exception e) {
            log.error("Bloom filter initialization failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                            BaseResponse.error(
                                    "BLOOM_FILTER_INIT_FAILED",
                                    "Không thể khởi tạo Bloom filter: " + e.getMessage()));
        }
    }
}

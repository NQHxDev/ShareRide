package com.shareride.auth.controller;

import com.shareride.auth.service.EmailBloomFilterService;
import com.shareride.common.response.BaseResponse;
import com.shareride.common.versioning.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin controller for Bloom filter management operations.
 *
 * <p>Provides endpoints for rebuild and initialization. All endpoints require ADMIN role via
 * {@code @PreAuthorize("hasRole('ADMIN')")} and return 401 Unauthorized for unauthenticated
 * requests and 403 Forbidden for non-admin authenticated users.
 *
 * <p>Concurrent rebuild operations are prevented using an atomic lock to avoid data corruption.
 */
@Slf4j
@RestController
@RequestMapping("/auth/v1/admin/bloom-filter")
@ApiVersion("v1")
@Tag(name = "Admin - Bloom Filter", description = "Quản lý Bloom filter cho email")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBloomFilterController {

    private final EmailBloomFilterService emailBloomFilterService;
    private final AtomicBoolean rebuildInProgress = new AtomicBoolean(false);

    public AdminBloomFilterController(EmailBloomFilterService emailBloomFilterService) {
        this.emailBloomFilterService = emailBloomFilterService;
    }

    /**
     * Rebuilds Bloom filter from PostgreSQL account data.
     *
     * <p>This operation clears existing filter and repopulates from database. Requires ADMIN role.
     * Concurrent rebuild operations are rejected to prevent data corruption.
     *
     * @return number of emails added to rebuilt filter
     */
    @PostMapping("/rebuild")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Xây dựng lại Bloom filter từ database",
            description =
                    "Xóa Bloom filter hiện tại và xây dựng lại từ dữ liệu PostgreSQL. Yêu cầu quyền"
                            + " ADMIN. Chặn các yêu cầu đồng thời để tránh xung đột.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Bloom filter đã được xây dựng lại thành công",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = BaseResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Yêu cầu chưa được xác thực hoặc Access Token không hợp lệ",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = BaseResponse.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "Không có quyền truy cập tài nguyên này (yêu cầu ADMIN)",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = BaseResponse.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Bloom filter rebuild đang được thực hiện",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = BaseResponse.class))),
                @ApiResponse(
                        responseCode = "500",
                        description = "Lỗi server khi xây dựng lại Bloom filter",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = BaseResponse.class)))
            })
    public ResponseEntity<BaseResponse<Map<String, Long>>> rebuildBloomFilter() {
        log.info("Admin rebuild Bloom filter request received");

        if (!rebuildInProgress.compareAndSet(false, true)) {
            log.warn("Bloom filter rebuild rejected: operation already in progress");
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(
                            BaseResponse.error(
                                    "BLOOM_FILTER_REBUILD_IN_PROGRESS",
                                    "Bloom filter rebuild đang được thực hiện. Vui lòng thử lại"
                                            + " sau."));
        }

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
        } finally {
            rebuildInProgress.set(false);
        }
    }

    /**
     * Manually initializes Bloom filter.
     *
     * <p>Useful for first-time setup or recovery. Requires ADMIN role.
     *
     * @return initialization status
     */
    @PostMapping("/initialize")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Khởi tạo Bloom filter",
            description = "Khởi tạo Bloom filter nếu chưa tồn tại. Yêu cầu quyền ADMIN.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Bloom filter đã được khởi tạo thành công",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = BaseResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Yêu cầu chưa được xác thực hoặc Access Token không hợp lệ",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = BaseResponse.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "Không có quyền truy cập tài nguyên này (yêu cầu ADMIN)",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = BaseResponse.class))),
                @ApiResponse(
                        responseCode = "500",
                        description = "Lỗi server khi khởi tạo Bloom filter",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = BaseResponse.class)))
            })
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

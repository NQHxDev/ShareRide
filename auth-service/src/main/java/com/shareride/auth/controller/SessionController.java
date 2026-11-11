package com.shareride.auth.controller;

import com.shareride.auth.constant.AuthConstants;
import com.shareride.auth.constant.AuthMessage;
import com.shareride.auth.constant.AuthResponseCode;
import com.shareride.auth.dto.SessionResponse;
import com.shareride.auth.service.SessionManagementService;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.openapi.OpenApiAutoConfiguration;
import com.shareride.common.response.BaseResponse;
import com.shareride.common.versioning.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller chuyên trách tiếp nhận và xử lý các yêu cầu quản lý phiên làm việc đa thiết bị
 * (Session Management), tra cứu phiên và thu hồi phiên từ xa.
 */
@Tag(
        name = "Session Management",
        description =
                "Cung cấp các API truy vấn danh sách phiên làm việc đang hoạt động và thu hồi phiên"
                        + " từ xa.")
@RestController
@RequestMapping(AuthConstants.Endpoint.BASE_PATH)
@ApiVersion("v1")
@RequiredArgsConstructor
public class SessionController {

    private final SessionManagementService sessionManagementService;

    /**
     * Lấy danh sách các phiên đăng nhập đang hoạt động của tài khoản.
     *
     * @param accountId ID của tài khoản
     * @return danh sách các phiên đăng nhập đang active
     */
    @Operation(
            summary = "Lấy danh sách các phiên đăng nhập đang hoạt động",
            description =
                    "Truy xuất tất cả các phiên làm việc (Session) đang ở trạng thái ACTIVE trên"
                        + " các thiết bị thuộc sở hữu của tài khoản, kèm thời điểm hoạt động gần"
                        + " nhất và địa chỉ IP.",
            security = {
                @SecurityRequirement(name = OpenApiAutoConfiguration.SECURITY_SCHEME_BEARER),
                @SecurityRequirement(name = OpenApiAutoConfiguration.SECURITY_SCHEME_CORRELATION_ID)
            })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Danh sách phiên hoạt động được truy xuất thành công",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Yêu cầu chưa được xác thực",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Không có quyền xem danh sách phiên của tài khoản này",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Lỗi hệ thống nội bộ khi truy vấn phiên làm việc",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping(AuthConstants.Endpoint.SESSIONS)
    public ResponseEntity<BaseResponse<List<SessionResponse>>> getActiveSessions(
            @Parameter(
                            description = "UUID tài khoản cần lấy danh sách phiên làm việc",
                            required = true,
                            example = "018d9f1e-8742-7ef1-95c2-f123456789ab")
                    @RequestParam("accountId")
                    UUID accountId) {
        UserContextHolder.requireSelfOrAdmin(accountId);
        List<SessionResponse> responses = sessionManagementService.getActiveSessions(accountId);
        return ResponseEntity.ok(BaseResponse.success(responses));
    }

    /**
     * Thu hồi phiên đăng nhập của thiết bị từ xa.
     *
     * @param sessionId ID của phiên đăng nhập cần thu hồi
     * @param accountId ID của tài khoản sở hữu phiên (tùy chọn để xác thực quyền)
     * @return phản hồi HTTP 200
     */
    @Operation(
            summary = "Thu hồi phiên đăng nhập của thiết bị từ xa",
            description =
                    "Đánh dấu trạng thái phiên chỉ định là REVOKED và lập tức xóa Refresh Token"
                        + " liên quan trong Valkey. Thiết bị sở hữu phiên này sẽ bị từ chối khi gia"
                        + " hạn token.",
            security = {
                @SecurityRequirement(name = OpenApiAutoConfiguration.SECURITY_SCHEME_BEARER),
                @SecurityRequirement(name = OpenApiAutoConfiguration.SECURITY_SCHEME_CORRELATION_ID)
            })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Thu hồi phiên làm việc thành công",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Yêu cầu chưa được xác thực",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Không có quyền thu hồi phiên này",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Không tìm thấy phiên làm việc với Session ID đã cho",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Lỗi hệ thống nội bộ khi thu hồi phiên",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class)))
    })
    @DeleteMapping(AuthConstants.Endpoint.SESSION_BY_ID)
    public ResponseEntity<BaseResponse<Void>> revokeSession(
            @Parameter(
                            description = "UUID của phiên đăng nhập cần thu hồi",
                            required = true,
                            example = "018d9f1e-8742-7ef1-95c2-f123456789ab")
                    @PathVariable(AuthConstants.PATH_VARIABLE_SESSION_ID)
                    UUID sessionId,
            @Parameter(
                            description =
                                    "UUID của tài khoản sở hữu phiên (tùy chọn để xác minh quyền"
                                            + " hạn)",
                            example = "018d9f1e-8742-7ef1-95c2-f123456789ac")
                    @RequestParam(value = "accountId", required = false)
                    UUID accountId) {
        sessionManagementService.revokeSession(sessionId, accountId);
        return ResponseEntity.ok(
                BaseResponse.success(
                        AuthResponseCode.SESSION_REVOKED,
                        AuthMessage.SESSION_REVOKED_SUCCESS,
                        null));
    }
}

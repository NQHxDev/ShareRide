package com.shareride.auth.controller;

import com.shareride.auth.constant.AuthConstants;
import com.shareride.auth.dto.AccountResponse;
import com.shareride.auth.service.AccountManagementService;
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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller chuyên trách tiếp nhận và xử lý các yêu cầu quản lý và tra cứu tài khoản người dùng
 * (Account Management).
 */
@Tag(
        name = "Account Management",
        description = "Cung cấp các API tra cứu và quản lý thông tin tài khoản người dùng.")
@RestController
@RequestMapping(AuthConstants.Endpoint.BASE_PATH)
@ApiVersion("v1")
@RequiredArgsConstructor
public class AccountController {

    private final AccountManagementService accountManagementService;

    /**
     * Tra cứu thông tin chi tiết của tài khoản theo Account ID.
     *
     * @param id UUID của tài khoản
     * @return chi tiết tài khoản và vai trò RBAC
     */
    @Operation(
            summary = "Tra cứu thông tin tài khoản theo Account ID",
            description =
                    "Truy xuất thông tin định danh, trạng thái tài khoản, và danh sách vai trò /"
                            + " quyền hạn RBAC tương ứng với UUID đã cung cấp.",
            security = {
                @SecurityRequirement(name = OpenApiAutoConfiguration.SECURITY_SCHEME_BEARER),
                @SecurityRequirement(name = OpenApiAutoConfiguration.SECURITY_SCHEME_CORRELATION_ID)
            })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Tra cứu thành công, trả về chi tiết thông tin tài khoản",
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
                description = "Không có quyền truy cập dữ liệu của tài khoản này",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Không tìm thấy tài khoản với Account ID đã cho",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Lỗi hệ thống nội bộ khi truy xuất tài khoản",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping(AuthConstants.Endpoint.ACCOUNTS_BY_ID)
    public ResponseEntity<BaseResponse<AccountResponse>> getAccount(
            @Parameter(
                            description = "UUID của tài khoản cần truy xuất",
                            required = true,
                            example = "018d9f1e-8742-7ef1-95c2-f123456789ab")
                    @PathVariable(AuthConstants.PATH_VARIABLE_ID)
                    UUID id) {
        UserContextHolder.requireSelfOrAdmin(id);
        AccountResponse response = accountManagementService.getAccountById(id);
        return ResponseEntity.ok(BaseResponse.success(response));
    }
}

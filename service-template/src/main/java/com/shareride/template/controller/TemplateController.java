package com.shareride.template.controller;

import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.openapi.OpenApiAutoConfiguration;
import com.shareride.common.response.BaseResponse;
import com.shareride.common.versioning.ApiVersion;
import com.shareride.template.constant.TemplateConstants;
import com.shareride.template.constant.TemplateMessage;
import com.shareride.template.constant.TemplateResponseCode;
import com.shareride.template.dto.CreateTemplateItemRequest;
import com.shareride.template.dto.TemplateItemResponse;
import com.shareride.template.dto.UpdateTemplateItemRequest;
import com.shareride.template.service.TemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller exposing Template Item APIs. Follows ShareRide Enterprise API standards:
 *
 * <ul>
 *   <li>Versioned via @ApiVersion("v1")
 *   <li>Enclosed in BaseResponse&lt;T&gt; wrapper
 *   <li>Input validation with @Valid
 *   <li>Standardized OpenAPI 3.0 documentation
 * </ul>
 */
@Tag(
        name = "Template Management",
        description =
                "Cung cấp các API quản lý vòng đời mục mẫu (Template Items): Tạo mới, tra cứu theo"
                        + " ID/Code, cập nhật thông tin và xóa mềm (Soft Delete).")
@RestController
@RequestMapping(TemplateConstants.Endpoint.BASE_PATH)
@ApiVersion("v1")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @Operation(
            summary = "Tạo mới một mục mẫu (Template Item)",
            description =
                    "Tạo mới một mục mẫu với mã định danh duy nhất (code), tên và mô tả. Tự động"
                        + " sinh ID theo chuẩn UUIDv7, gán phiên bản ban đầu (version = 0) và phát"
                        + " sinh sự kiện khởi tạo hệ thống.",
            security = {
                @SecurityRequirement(name = OpenApiAutoConfiguration.SECURITY_SCHEME_BEARER),
                @SecurityRequirement(name = OpenApiAutoConfiguration.SECURITY_SCHEME_CORRELATION_ID)
            })
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Mục mẫu được khởi tạo thành công",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Dữ liệu yêu cầu không hợp lệ (thiếu tên, mã mục vượt độ dài)",
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
                responseCode = "409",
                description = "Mã mục mẫu (code) đã tồn tại trên hệ thống",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Lỗi hệ thống nội bộ khi lưu trữ mục mẫu",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping
    public ResponseEntity<BaseResponse<TemplateItemResponse>> createItem(
            @Valid @RequestBody CreateTemplateItemRequest request) {
        TemplateItemResponse response = templateService.createItem(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        BaseResponse.success(
                                TemplateResponseCode.TEMPLATE_ITEM_CREATED,
                                TemplateMessage.ITEM_CREATED_SUCCESS,
                                response));
    }

    @Operation(
            summary = "Tra cứu mục mẫu theo UUID",
            description =
                    "Truy xuất thông tin chi tiết của một mục mẫu theo khóa chính UUIDv7. Chỉ trả"
                            + " về dữ liệu nếu bản ghi chưa bị xóa mềm.",
            security = {
                @SecurityRequirement(name = OpenApiAutoConfiguration.SECURITY_SCHEME_BEARER),
                @SecurityRequirement(name = OpenApiAutoConfiguration.SECURITY_SCHEME_CORRELATION_ID)
            })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Tra cứu thành công, trả về thông tin chi tiết mục mẫu",
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
                responseCode = "404",
                description = "Không tìm thấy mục mẫu với UUID tương ứng",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Lỗi hệ thống nội bộ khi truy xuất mục mẫu",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping(TemplateConstants.Endpoint.BY_ID)
    public ResponseEntity<BaseResponse<TemplateItemResponse>> getItemById(
            @Parameter(
                            description = "UUID của mục mẫu cần tra cứu",
                            required = true,
                            example = "018d9f1e-8742-7ef1-95c2-f123456789ab")
                    @PathVariable(TemplateConstants.PATH_VARIABLE_ID)
                    UUID id) {
        TemplateItemResponse response = templateService.getItemById(id);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    @Operation(
            summary = "Tra cứu mục mẫu theo mã nghiệp vụ (Code)",
            description =
                    "Truy xuất thông tin mục mẫu dựa trên mã định danh duy nhất (business code)."
                            + " Bản ghi đã bị xóa mềm sẽ tự động được lọc bỏ.",
            security = {
                @SecurityRequirement(name = OpenApiAutoConfiguration.SECURITY_SCHEME_BEARER),
                @SecurityRequirement(name = OpenApiAutoConfiguration.SECURITY_SCHEME_CORRELATION_ID)
            })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Tìm thấy mục mẫu tương ứng với mã nghiệp vụ",
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
                responseCode = "404",
                description = "Không tìm thấy mục mẫu với mã code đã cung cấp",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Lỗi hệ thống nội bộ khi tìm kiếm",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class)))
    })
    @GetMapping("/code/{code}")
    public ResponseEntity<BaseResponse<TemplateItemResponse>> getItemByCode(
            @Parameter(
                            description = "Mã nghiệp vụ của mục mẫu",
                            required = true,
                            example = "VEHICLE_STANDARD_4S")
                    @PathVariable("code")
                    String code) {
        TemplateItemResponse response = templateService.getItemByCode(code);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    @Operation(
            summary = "Cập nhật thông tin mục mẫu",
            description =
                    "Cập nhật tên, mô tả hoặc trạng thái của mục mẫu. Áp dụng cơ chế khóa lạc quan"
                        + " (Optimistic Locking) thông qua trường version để ngăn chặn xung đột ghi"
                        + " đè dữ liệu đồng thời (Concurrent Modification).",
            security = {
                @SecurityRequirement(name = OpenApiAutoConfiguration.SECURITY_SCHEME_BEARER),
                @SecurityRequirement(name = OpenApiAutoConfiguration.SECURITY_SCHEME_CORRELATION_ID)
            })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Cập nhật thông tin mục mẫu thành công",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Dữ liệu cập nhật không hợp lệ",
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
                responseCode = "404",
                description = "Không tìm thấy mục mẫu để cập nhật",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Xung đột phiên bản dữ liệu (Optimistic Locking failure)",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Lỗi hệ thống nội bộ khi cập nhật mục mẫu",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class)))
    })
    @PutMapping(TemplateConstants.Endpoint.BY_ID)
    public ResponseEntity<BaseResponse<TemplateItemResponse>> updateItem(
            @Parameter(
                            description = "UUID của mục mẫu cần cập nhật",
                            required = true,
                            example = "018d9f1e-8742-7ef1-95c2-f123456789ab")
                    @PathVariable(TemplateConstants.PATH_VARIABLE_ID)
                    UUID id,
            @Valid @RequestBody UpdateTemplateItemRequest request) {
        TemplateItemResponse response = templateService.updateItem(id, request);
        return ResponseEntity.ok(
                BaseResponse.success(
                        TemplateResponseCode.TEMPLATE_ITEM_UPDATED,
                        TemplateMessage.ITEM_UPDATED_SUCCESS,
                        response));
    }

    @Operation(
            summary = "Xóa mềm (Soft Delete) mục mẫu",
            description =
                    "Đánh dấu trường isDeleted = true và lưu thông tin người xóa cùng thời điểm xóa"
                        + " (deletedAt, deletedBy). Dữ liệu không bị xóa vật lý khỏi cơ sở dữ liệu"
                        + " để phục vụ kiểm toán và tính toàn vẹn tham chiếu.",
            security = {
                @SecurityRequirement(name = OpenApiAutoConfiguration.SECURITY_SCHEME_BEARER),
                @SecurityRequirement(name = OpenApiAutoConfiguration.SECURITY_SCHEME_CORRELATION_ID)
            })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Xóa mềm mục mẫu thành công",
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
                responseCode = "404",
                description = "Không tìm thấy mục mẫu để xóa",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Lỗi hệ thống nội bộ khi thực hiện xóa mềm",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class)))
    })
    @DeleteMapping(TemplateConstants.Endpoint.BY_ID)
    public ResponseEntity<BaseResponse<Void>> deleteItem(
            @Parameter(
                            description = "UUID của mục mẫu cần xóa",
                            required = true,
                            example = "018d9f1e-8742-7ef1-95c2-f123456789ab")
                    @PathVariable(TemplateConstants.PATH_VARIABLE_ID)
                    UUID id) {
        UserContext context = UserContextHolder.getContext();
        String deletedBy =
                (context != null && context.getUserId() != null) ? context.getUserId() : "SYSTEM";
        templateService.deleteItem(id, deletedBy);
        return ResponseEntity.ok(BaseResponse.emptySuccess());
    }
}

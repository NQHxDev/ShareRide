package com.shareride.auth.controller;

import com.shareride.auth.constant.AuthConstants;
import com.shareride.auth.constant.AuthMessage;
import com.shareride.auth.constant.AuthResponseCode;
import com.shareride.auth.dto.AccountResponse;
import com.shareride.auth.dto.AuthResponse;
import com.shareride.auth.dto.LoginRequest;
import com.shareride.auth.dto.RefreshTokenRequest;
import com.shareride.auth.dto.RegisterRequest;
import com.shareride.auth.dto.SessionResponse;
import com.shareride.auth.service.AuthService;
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
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller tiếp nhận và xử lý các yêu cầu liên quan đến xác thực và phân quyền (Authentication /
 * Authorization), quản lý phiên làm việc và tài khoản.
 */
@Tag(
        name = "Authentication & Identity",
        description =
                "Cung cấp các API xác thực danh tính người dùng, đăng ký, đăng nhập, xoay vòng"
                        + " Refresh Token (Token Rotation), thu hồi phiên và công bố khóa JWKS RFC"
                        + " 7517.")
@RestController
@RequestMapping(AuthConstants.Endpoint.BASE_PATH)
@ApiVersion("v1")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Đăng ký tài khoản xác thực mới.
     *
     * @param request dữ liệu đăng ký chứa email, password, phoneNumber, roleCode...
     * @return chi tiết tài khoản vừa tạo (HTTP 201)
     */
    @Operation(
            summary = "Đăng ký tài khoản người dùng mới",
            description =
                    "Tạo tài khoản xác thực mới với email và mật khẩu được băm an toàn"
                        + " (BCrypt/Argon2id). Hệ thống kiểm tra tính duy nhất của email và số điện"
                        + " thoại, gán vai trò mặc định (ROLE_RIDER), và phát sinh sự kiện"
                        + " Transactional Outbox (USER_REGISTERED) để đồng bộ sang User Profile"
                        + " Service.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Tài khoản được đăng ký thành công",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description =
                        "Dữ liệu yêu cầu không hợp lệ (lỗi định dạng email, mật khẩu quá ngắn,"
                                + " thiếu trường bắt buộc)",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Email hoặc số điện thoại đã tồn tại trên hệ thống",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "429",
                description = "Vượt quá giới hạn tần suất yêu cầu đăng ký (Rate limit)",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Lỗi hệ thống nội bộ trong quá trình xử lý đăng ký",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class)))
    })
    @SecurityRequirements()
    @PostMapping(AuthConstants.Endpoint.REGISTER)
    public ResponseEntity<BaseResponse<AccountResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        AccountResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        BaseResponse.success(
                                AuthResponseCode.ACCOUNT_REGISTERED,
                                AuthMessage.ACCOUNT_REGISTERED_SUCCESS,
                                response));
    }

    /**
     * Xác thực thông tin đăng nhập và cấp phát token cùng phiên làm việc trên thiết bị.
     *
     * @param request thông tin đăng nhập và metadata thiết bị
     * @param servletRequest đối tượng HttpServletRequest để trích xuất IP và User-Agent
     * @return cặp Access Token, Refresh Token, Session ID và thông tin tài khoản (HTTP 200)
     */
    @Operation(
            summary = "Xác thực đăng nhập và cấp phát Access/Refresh Token",
            description =
                    "Kiểm tra tính hợp lệ của thông tin đăng nhập email/mật khẩu, kiểm tra trạng"
                        + " thái khóa tài khoản và số lần thử sai. Nếu thành công, hệ thống khởi"
                        + " tạo Session mới gắn với thiết bị, cấp Access Token ký số EdDSA"
                        + " (Ed25519) và lưu Refresh Token (opaque token) vào bộ nhớ phân tán"
                        + " Valkey kèm thời gian sống (TTL).")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description =
                        "Xác thực thành công, trả về Access Token, Refresh Token và thông tin"
                                + " Session",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description =
                        "Thông tin đăng nhập gửi lên không hợp lệ hoặc thiếu thông tin bắt buộc",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Email hoặc mật khẩu không chính xác",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Tài khoản đang bị tạm khóa (LOCKED) hoặc vô hiệu hóa (INACTIVE)",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "429",
                description = "Vượt quá số lần thử đăng nhập cho phép (Chống tấn công Brute-force)",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Lỗi hệ thống nội bộ khi cấp phát phiên hoặc token",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class)))
    })
    @SecurityRequirements()
    @PostMapping(AuthConstants.Endpoint.LOGIN)
    public ResponseEntity<BaseResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String clientIp = extractClientIp(servletRequest);
        String userAgent = servletRequest != null ? servletRequest.getHeader("User-Agent") : null;
        AuthResponse response = authService.login(request, clientIp, userAgent);
        return ResponseEntity.ok(
                BaseResponse.success(
                        AuthResponseCode.LOGIN_SUCCESS, AuthMessage.LOGIN_SUCCESS, response));
    }

    /**
     * Làm mới Access Token và xoay vòng Refresh Token (Token Rotation).
     *
     * @param request dữ liệu chứa refresh token hiện tại
     * @param servletRequest đối tượng HttpServletRequest
     * @return cặp Access Token và Refresh Token mới (HTTP 200)
     */
    @Operation(
            summary = "Làm mới Access Token và xoay vòng Refresh Token (Token Rotation)",
            description =
                    "Kiểm tra Refresh Token được lưu trong bộ nhớ Valkey và trạng thái phiên tương"
                        + " ứng. Áp dụng cơ chế xoay vòng Refresh Token nghiêm ngặt: Refresh Token"
                        + " cũ ngay lập tức bị vô hiệu hóa, và một cặp Access Token + Refresh Token"
                        + " mới được sinh ra. Nếu phát hiện token đã bị dùng lại (Token Replay),"
                        + " toàn bộ phiên của người dùng sẽ bị thu hồi ngay lập tức để bảo vệ tài"
                        + " khoản.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Làm mới token thành công, trả về cặp token mới",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Dữ liệu gửi lên thiếu Refresh Token",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description =
                        "Refresh Token không hợp lệ, đã hết hạn, hoặc bị tái sử dụng trái phép",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "429",
                description = "Tần suất làm mới token vượt ngưỡng cho phép",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Lỗi hệ thống nội bộ khi gia hạn token",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class)))
    })
    @SecurityRequirements()
    @PostMapping(AuthConstants.Endpoint.REFRESH)
    public ResponseEntity<BaseResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request, HttpServletRequest servletRequest) {
        String clientIp = extractClientIp(servletRequest);
        String userAgent = servletRequest != null ? servletRequest.getHeader("User-Agent") : null;
        AuthResponse response =
                authService.refreshToken(request.getRefreshToken(), clientIp, userAgent);
        return ResponseEntity.ok(
                BaseResponse.success(
                        AuthResponseCode.TOKEN_REFRESHED, AuthMessage.REFRESH_SUCCESS, response));
    }

    /**
     * Đăng xuất người dùng, đưa Access Token vào Redis Blacklist và xóa Refresh Token khỏi Valkey.
     *
     * @param authHeader chuỗi Authorization Header chứa Bearer token cần thu hồi
     * @param refreshToken chuỗi Refresh Token cần hủy
     * @return phản hồi HTTP 200 thông báo đăng xuất thành công
     */
    @Operation(
            summary = "Đăng xuất tài khoản & Thu hồi token",
            description =
                    "Thu hồi Access Token hiện tại bằng cách lưu JTI (JWT ID) vào Redis Blacklist"
                        + " với thời gian sống TTL bằng thời gian còn lại của token. Đồng thời xóa"
                        + " Refresh Token khỏi Valkey và cập nhật trạng thái phiên (Session) sang"
                        + " TERMINATED.",
            security = {
                @SecurityRequirement(name = OpenApiAutoConfiguration.SECURITY_SCHEME_BEARER),
                @SecurityRequirement(name = OpenApiAutoConfiguration.SECURITY_SCHEME_CORRELATION_ID)
            })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Đăng xuất thành công, tất cả token liên quan đã bị hủy",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Yêu cầu đăng xuất không hợp lệ",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Access Token không hợp lệ hoặc đã bị vô hiệu hóa từ trước",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class))),
        @ApiResponse(
                responseCode = "500",
                description = "Lỗi hệ thống nội bộ khi đăng xuất",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping(AuthConstants.Endpoint.LOGOUT)
    public ResponseEntity<BaseResponse<Void>> logout(
            @Parameter(
                            description = "Chuỗi Bearer Access Token cần thu hồi",
                            example = "Bearer eyJhbGciOiJFZERTQS...")
                    @RequestHeader(value = AuthConstants.HEADER_AUTHORIZATION, required = false)
                    String authHeader,
            @Parameter(
                            description = "Mã Refresh Token cần hủy trong Valkey",
                            example = "d1b033aa-448f-4ba3-a442-5cb0098dfca1")
                    @RequestParam(value = AuthConstants.PARAM_REFRESH_TOKEN, required = false)
                    String refreshToken) {
        String token = null;
        if (authHeader != null && authHeader.startsWith(AuthConstants.BEARER_PREFIX)) {
            token = authHeader.substring(AuthConstants.BEARER_PREFIX_LENGTH);
        }
        authService.logout(token, refreshToken);
        return ResponseEntity.ok(BaseResponse.emptySuccess());
    }

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
        AccountResponse response = authService.getAccountById(id);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

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
        List<SessionResponse> responses = authService.getActiveSessions(accountId);
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
        authService.revokeSession(sessionId, accountId);
        return ResponseEntity.ok(
                BaseResponse.success(
                        AuthResponseCode.SESSION_REVOKED,
                        AuthMessage.SESSION_REVOKED_SUCCESS,
                        null));
    }

    /**
     * Cung cấp tập khóa công khai JWKS (RFC 7517) cho API Gateway và Resource Servers dùng để xác
     * thực chữ ký số EdDSA của Access Token một cách độc lập (Stateless).
     *
     * @return Tập khóa công khai JWKS định dạng JSON chuẩn RFC 7517
     */
    @Operation(
            summary = "Khám phá tập khóa công khai JWKS (RFC 7517)",
            description =
                    "Cung cấp khóa công khai Ed25519 (EdDSA) dưới định dạng chuẩn RFC 7517 JSON Web"
                        + " Key Set (JWKS). API Gateway và các Microservices tải tập khóa này về"
                        + " cache để xác thực chữ ký số của Access Token độc lập mà không cần gửi"
                        + " request kiểm tra tới Auth Service.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Tập khóa công khai JWKS định dạng RFC 7517 JSON",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema =
                                        @Schema(
                                                example =
                                                        "{\"keys\":[{\"kty\":\"OKP\",\"crv\":\"Ed25519\","
                                                            + "\"kid\":\"shareride-auth-key-1\",\"x\":\"...\"}]}"))),
        @ApiResponse(
                responseCode = "500",
                description = "Lỗi máy chủ nội bộ khi xuất bản khóa công khai",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = BaseResponse.class)))
    })
    @SecurityRequirements()
    @GetMapping(value = AuthConstants.Endpoint.JWKS, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getJwks() {
        return ResponseEntity.ok(authService.getJwks());
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

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
import com.shareride.common.response.BaseResponse;
import com.shareride.common.versioning.ApiVersion;
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
    @PostMapping(AuthConstants.Endpoint.LOGOUT)
    public ResponseEntity<BaseResponse<Void>> logout(
            @RequestHeader(value = AuthConstants.HEADER_AUTHORIZATION, required = false)
                    String authHeader,
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
    @GetMapping(AuthConstants.Endpoint.ACCOUNTS_BY_ID)
    public ResponseEntity<BaseResponse<AccountResponse>> getAccount(
            @PathVariable(AuthConstants.PATH_VARIABLE_ID) UUID id) {
        AccountResponse response = authService.getAccountById(id);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    /**
     * Lấy danh sách các phiên đăng nhập đang hoạt động của tài khoản.
     *
     * @param accountId ID của tài khoản
     * @return danh sách các phiên đăng nhập đang active
     */
    @GetMapping(AuthConstants.Endpoint.SESSIONS)
    public ResponseEntity<BaseResponse<List<SessionResponse>>> getActiveSessions(
            @RequestParam("accountId") UUID accountId) {
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
    @DeleteMapping(AuthConstants.Endpoint.SESSION_BY_ID)
    public ResponseEntity<BaseResponse<Void>> revokeSession(
            @PathVariable(AuthConstants.PATH_VARIABLE_SESSION_ID) UUID sessionId,
            @RequestParam(value = "accountId", required = false) UUID accountId) {
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

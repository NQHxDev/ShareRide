package com.shareride.auth.controller;

import com.shareride.auth.constant.AuthConstants;
import com.shareride.auth.constant.AuthMessage;
import com.shareride.auth.constant.AuthResponseCode;
import com.shareride.auth.dto.AuthResponse;
import com.shareride.auth.dto.LoginRequest;
import com.shareride.auth.dto.RegisterRequest;
import com.shareride.auth.dto.UserResponse;
import com.shareride.auth.service.AuthService;
import com.shareride.common.response.BaseResponse;
import com.shareride.common.versioning.ApiVersion;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller tiếp nhận và xử lý các yêu cầu liên quan đến xác thực và phân quyền (Authentication /
 * Authorization). Bao gồm các nghiệp vụ: Đăng ký tài khoản, Đăng nhập cấp phát token, Đăng xuất thu
 * hồi token và Tra cứu người dùng.
 */
@RestController
@RequestMapping(AuthConstants.Endpoint.BASE_PATH)
@ApiVersion("v1")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Đăng ký tài khoản người dùng mới (Rider hoặc Driver).
     *
     * @param request dữ liệu đăng ký chứa email, password, fullName, phoneNumber, role
     * @return thông tin người dùng vừa được tạo cùng mã phản hồi {@link
     *     AuthResponseCode#USER_REGISTERED} (HTTP 201)
     */
    @PostMapping(AuthConstants.Endpoint.REGISTER)
    public ResponseEntity<BaseResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        BaseResponse.success(
                                AuthResponseCode.USER_REGISTERED,
                                AuthMessage.USER_REGISTERED_SUCCESS,
                                response));
    }

    /**
     * Xác thực thông tin đăng nhập và cấp phát token truy cập.
     *
     * @param request thông tin đăng nhập gồm email và mật khẩu
     * @return cặp Access Token và Refresh Token cùng mã phản hồi {@link
     *     AuthResponseCode#LOGIN_SUCCESS} (HTTP 200)
     */
    @PostMapping(AuthConstants.Endpoint.LOGIN)
    public ResponseEntity<BaseResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(
                BaseResponse.success(
                        AuthResponseCode.LOGIN_SUCCESS, AuthMessage.LOGIN_SUCCESS, response));
    }

    /**
     * Đăng xuất người dùng, đưa Access Token vào Redis Blacklist và thu hồi Refresh Token trong DB.
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
     * Tra cứu thông tin chi tiết của người dùng theo UUID định danh.
     *
     * @param id UUID của tài khoản người dùng
     * @return chi tiết hồ sơ người dùng
     */
    @GetMapping(AuthConstants.Endpoint.USERS_BY_ID)
    public ResponseEntity<BaseResponse<UserResponse>> getUser(
            @PathVariable(AuthConstants.PATH_VARIABLE_ID) UUID id) {
        UserResponse response = authService.getUserById(id);
        return ResponseEntity.ok(BaseResponse.success(response));
    }
}

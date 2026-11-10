package com.shareride.auth.controller;

import com.shareride.auth.service.AuthService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller phục vụ các endpoint khám phá chuẩn RFC 7517 / RFC 8414 (Well-Known URIs). Cho phép
 * các OAuth2/OIDC Resource Server lấy JWKS tại đường dẫn chuẩn /.well-known/jwks.json.
 */
@RestController
@RequiredArgsConstructor
public class WellKnownController {

    private final AuthService authService;

    /**
     * Cung cấp JWKS theo định dạng chuẩn RFC 7517 tại đường dẫn Well-Known.
     *
     * @return Tập khóa công khai JWKS định dạng JSON
     */
    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getJwks() {
        return ResponseEntity.ok(authService.getJwks());
    }
}

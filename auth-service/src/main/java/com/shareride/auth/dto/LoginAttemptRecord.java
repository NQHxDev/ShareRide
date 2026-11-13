package com.shareride.auth.dto;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** DTO lưu trữ số lần thử đăng nhập và thời hạn hiệu lực cho in-memory rate limiting fallback. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginAttemptRecord implements Serializable {

    private long expiresAt;
    private AtomicInteger count;
}

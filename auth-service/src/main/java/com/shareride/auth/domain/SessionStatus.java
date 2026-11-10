package com.shareride.auth.domain;

/** Trạng thái của phiên đăng nhập (Session) trên thiết bị người dùng. */
public enum SessionStatus {
    ACTIVE,
    REVOKED,
    EXPIRED
}

package com.shareride.auth.domain;

/** Trạng thái hoạt động của tài khoản trong Auth Service. */
public enum AccountStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    PENDING_VERIFICATION
}

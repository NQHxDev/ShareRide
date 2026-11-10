package com.shareride.auth.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Mã quyền hạn chi tiết (Granular Permission) trong mô hình RBAC của Auth Service. */
public enum PermissionCode {
    RIDE_CREATE("ride:create"),
    RIDE_READ("ride:read"),
    RIDE_ACCEPT("ride:accept"),
    ACCOUNT_READ("account:read"),
    ACCOUNT_WRITE("account:write"),
    SESSION_READ("session:read"),
    SESSION_REVOKE("session:revoke");

    private final String value;

    PermissionCode(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Phân giải chuỗi quyền hạn (ví dụ 'ride:create' hoặc 'RIDE_CREATE') sang PermissionCode.
     *
     * @param value chuỗi quyền hạn
     * @return PermissionCode tương ứng hoặc null nếu không tìm thấy
     */
    @JsonCreator
    public static PermissionCode fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (PermissionCode permissionCode : values()) {
            if (permissionCode.value.equalsIgnoreCase(value.trim())
                    || permissionCode.name().equalsIgnoreCase(value.trim())) {
                return permissionCode;
            }
        }
        return null;
    }
}

package com.shareride.auth.domain;

/** Mã vai trò chuẩn trong mô hình phân quyền (RBAC) của Auth Service. */
public enum RoleCode {
    ROLE_RIDER,
    ROLE_DRIVER,
    ROLE_ADMIN,
    ROLE_SUPPORT;

    /**
     * Chuyển đổi an toàn từ chuỗi (không phân biệt hoa thường) sang RoleCode.
     *
     * @param code chuỗi biểu diễn mã vai trò
     * @return RoleCode tương ứng hoặc null nếu không hợp lệ
     */
    public static RoleCode fromString(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (RoleCode roleCode : values()) {
            if (roleCode.name().equalsIgnoreCase(code.trim())) {
                return roleCode;
            }
        }
        return null;
    }
}

package com.shareride.auth.service;

import com.shareride.auth.domain.Permission;
import com.shareride.auth.domain.PermissionCode;
import com.shareride.auth.domain.Role;
import com.shareride.auth.domain.RoleCode;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service quản lý lưu trữ, truy xuất và làm mới bộ nhớ cache cho Role và Permission trên Valkey
 * dùng chung.
 *
 * <p>Được thiết kế để giải quyết triệt để vấn đề N+1 query problem, giảm tải tối đa cho cơ sở dữ
 * liệu bằng cách lưu trữ các vai trò và quyền hạn với thời gian sống (TTL) 6 tiếng.
 */
public interface RolePermissionCacheService {

    /**
     * Lấy thông tin Role theo RoleCode từ cache Valkey (nếu cache miss sẽ truy vấn DB và lưu cache
     * 6 tiếng).
     *
     * @param roleCode mã vai trò cần lấy
     * @return Optional chứa đối tượng Role (đã bao gồm permissions) nếu tồn tại
     */
    Optional<Role> getRoleByCode(RoleCode roleCode);

    /**
     * Lấy tập hợp Role từ cache Valkey theo danh sách RoleCodes.
     *
     * @param roleCodes danh sách các mã vai trò
     * @return tập hợp các Role tương ứng
     */
    Set<Role> getRolesByCodes(Collection<RoleCode> roleCodes);

    /**
     * Lấy danh sách các mã quyền hạn (permission code dạng string ví dụ "ride:create") của một Role
     * từ cache Valkey.
     *
     * @param roleCode mã vai trò
     * @return tập hợp các mã quyền hạn chuỗi
     */
    Set<String> getPermissionsByRoleCode(RoleCode roleCode);

    /**
     * Lấy danh sách mã quyền hạn tổng hợp của nhiều RoleCodes từ cache Valkey (TTL 6 tiếng).
     *
     * @param roleCodes danh sách các mã vai trò
     * @return tập hợp các mã quyền hạn chuỗi
     */
    Set<String> getPermissionsByRoleCodes(Collection<RoleCode> roleCodes);

    /**
     * Lấy thông tin chi tiết của Permission theo PermissionCode từ cache Valkey (TTL 6 tiếng).
     *
     * @param permissionCode mã quyền hạn
     * @return Optional chứa Permission nếu tồn tại
     */
    Optional<Permission> getPermissionByCode(PermissionCode permissionCode);

    /**
     * Lấy tất cả các Role kèm Permissions đã được cache trên Valkey (nếu miss sẽ nạp toàn bộ từ
     * DB).
     *
     * @return danh sách tất cả các Role
     */
    List<Role> getAllRoles();

    /**
     * Nạp trước (warm-up) hoặc làm mới toàn bộ dữ liệu Role và Permission vào Valkey với TTL 6
     * tiếng.
     */
    void refreshCache();

    /**
     * Xóa cache của một Role cụ thể khi có cập nhật.
     *
     * @param roleCode mã vai trò cần xóa cache
     */
    void evictRole(RoleCode roleCode);

    /**
     * Xóa cache của một Permission cụ thể khi có cập nhật.
     *
     * @param permissionCode mã quyền hạn cần xóa cache
     */
    void evictPermission(PermissionCode permissionCode);

    /** Xóa toàn bộ cache Role và Permission trên Valkey. */
    void evictAll();
}

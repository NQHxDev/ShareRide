package com.shareride.auth.service;

import com.shareride.auth.domain.Account;
import com.shareride.auth.dto.AccountResponse;
import java.util.UUID;

/**
 * Service quản lý thông tin tài khoản, xóa mềm và các thao tác liên quan đến Account trong Auth
 * Service.
 */
public interface AccountManagementService {

    /**
     * Lấy thông tin tài khoản theo Account ID.
     *
     * @param accountId ID của tài khoản
     * @return chi tiết tài khoản và vai trò RBAC
     */
    AccountResponse getAccountById(UUID accountId);

    /**
     * Tra cứu tài khoản đang ở trạng thái ACTIVE kèm vai trò RBAC phục vụ xác thực/gia hạn token.
     *
     * @param accountId ID tài khoản
     * @return thực thể tài khoản đang hoạt động
     */
    Account getActiveAccount(UUID accountId);

    /**
     * Xóa mềm tài khoản và hủy toàn bộ phiên làm việc của tài khoản.
     *
     * @param accountId ID của tài khoản
     * @param deletedBy người thực hiện thao tác xóa
     */
    void softDeleteAccount(UUID accountId, String deletedBy);

    /**
     * Chuyển đổi đối tượng thực thể {@link Account} sang DTO {@link AccountResponse} kèm quyền hạn.
     *
     * @param account thực thể tài khoản
     * @return DTO biểu diễn thông tin tài khoản
     */
    AccountResponse toAccountResponse(Account account);
}

package com.shareride.auth.repository;

import com.shareride.auth.domain.AccountVerification;
import com.shareride.auth.domain.VerificationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository quản lý thử thách xác thực email. Hỗ trợ truy vấn mã xác thực đang hoạt động, cập nhật
 * trạng thái nguyên tử, và vô hiệu hóa các mã cũ.
 */
@Repository
public interface AccountVerificationRepository extends JpaRepository<AccountVerification, UUID> {

    /**
     * Tìm thử thách xác thực mới nhất của tài khoản với trạng thái cụ thể.
     *
     * @param accountId ID tài khoản
     * @param status trạng thái xác thực
     * @return thử thách xác thực gần nhất theo thời gian tạo
     */
    Optional<AccountVerification> findFirstByAccountIdAndStatusOrderByCreatedAtDesc(
            UUID accountId, VerificationStatus status);

    /**
     * Tìm tất cả các thử thách xác thực của tài khoản với trạng thái cụ thể.
     *
     * @param accountId ID tài khoản
     * @param status trạng thái xác thực
     * @return danh sách tất cả thử thách phù hợp
     */
    List<AccountVerification> findAllByAccountIdAndStatus(
            UUID accountId, VerificationStatus status);

    /**
     * Cập nhật trạng thái của tất cả thử thách PENDING của tài khoản thành EXPIRED. Sử dụng cho
     * luồng gửi lại mã xác thực để vô hiệu hóa mã cũ.
     *
     * @param accountId ID tài khoản
     * @return số bản ghi được cập nhật
     */
    @Modifying
    @Query(
            "UPDATE AccountVerification av SET av.status = :expiredStatus, av.updatedAt ="
                    + " CURRENT_TIMESTAMP WHERE av.accountId = :accountId AND av.status ="
                    + " :pendingStatus")
    int expireAllPendingByAccountId(
            @Param("accountId") UUID accountId,
            @Param("pendingStatus") VerificationStatus pendingStatus,
            @Param("expiredStatus") VerificationStatus expiredStatus);
}

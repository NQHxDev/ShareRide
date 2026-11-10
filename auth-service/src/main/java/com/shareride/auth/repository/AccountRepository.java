package com.shareride.auth.repository;

import com.shareride.auth.domain.Account;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository truy xuất và quản lý dữ liệu tài khoản xác thực (Account). */
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    Optional<Account> findByEmailAndDeletedFalse(String email);

    Optional<Account> findByPhoneNumberAndDeletedFalse(String phoneNumber);
}

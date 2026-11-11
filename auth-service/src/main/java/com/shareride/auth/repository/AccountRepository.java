package com.shareride.auth.repository;

import com.shareride.auth.domain.Account;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository truy xuất và quản lý dữ liệu tài khoản xác thực (Account). */
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    @EntityGraph(attributePaths = {"roles"})
    Optional<Account> findByEmailAndDeletedFalse(String email);

    @EntityGraph(attributePaths = {"roles"})
    Optional<Account> findByPhoneNumberAndDeletedFalse(String phoneNumber);

    @EntityGraph(attributePaths = {"roles"})
    @Query("SELECT a FROM Account a WHERE a.id = :id AND a.deleted = false")
    Optional<Account> findActiveByIdWithRoles(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"roles"})
    Page<Account> findByDeletedFalse(Pageable pageable);

    @Query("SELECT a FROM Account a WHERE a.id IN :ids AND a.deleted = false")
    List<Account> findAllActiveByIds(@Param("ids") List<UUID> ids);
}

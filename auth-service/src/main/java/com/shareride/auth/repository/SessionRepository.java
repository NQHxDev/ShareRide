package com.shareride.auth.repository;

import com.shareride.auth.domain.Session;
import com.shareride.auth.domain.SessionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository quản lý phiên đăng nhập (Session) của tài khoản người dùng trên các thiết bị. */
@Repository
public interface SessionRepository extends JpaRepository<Session, UUID> {

    List<Session> findByAccountIdAndStatusOrderByLastActiveAtDesc(
            UUID accountId, SessionStatus status);

    List<Session> findByAccountIdOrderByLastActiveAtDesc(UUID accountId);

    Optional<Session> findByIdAndAccountId(UUID sessionId, UUID accountId);
}

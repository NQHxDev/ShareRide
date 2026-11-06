package com.shareride.auth.repository;

import com.shareride.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

   Optional<User> findByEmail(String email);

   Optional<User> findByEmailAndDeletedFalse(String email);

   boolean existsByEmail(String email);

   boolean existsByPhoneNumber(String phoneNumber);

}

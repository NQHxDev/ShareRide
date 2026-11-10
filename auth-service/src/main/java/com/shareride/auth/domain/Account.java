package com.shareride.auth.domain;

import com.shareride.common.persistence.SoftDeletableEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Thực thể quản lý tài khoản xác thực (Account) trong Auth Service.
 *
 * <p>Được phân tách hoàn toàn khỏi User Profile (thuộc User Service).
 */
@Entity
@Table(name = "accounts")
@AttributeOverride(
        name = "id",
        column = @Column(name = "account_id", updatable = false, nullable = false))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account extends SoftDeletableEntity {

    @Column(name = "email", nullable = false, length = 128, unique = true)
    private String email;

    @Column(name = "phone_number", nullable = false, length = 32, unique = true)
    private String phoneNumber;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "account_roles",
            joinColumns = @JoinColumn(name = "account_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    @Builder.Default
    private Set<Role> roles = new HashSet<>();
}

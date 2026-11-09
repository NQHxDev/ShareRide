package com.shareride.common.persistence;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.proxy.HibernateProxy;

/**
 * Foundational mapped superclass for all ShareRide microservice relational entities.
 *
 * <p>Key architectural design points: 1. Primary key is UUIDv7: Time-ordered UUID (RFC 9562) for
 * high throughput, optimal database B-tree index performance, and reduced fragmentation. 2.
 * Optimistic Locking: Managed via @Version to prevent lost updates in highly concurrent
 * ride-hailing scenarios (e.g., simultaneous driver acceptance, status updates). 3. Safe equals and
 * hashCode: Robust against Hibernate lazy proxies and unpersisted state.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity implements Serializable {

    /** UUIDv7 primary key (RFC 9562 time-ordered epoch). */
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @PrePersist
    protected void ensureId() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        Class<?> oEffectiveClass =
                o instanceof HibernateProxy proxy
                        ? proxy.getHibernateLazyInitializer().getPersistentClass()
                        : o.getClass();
        Class<?> thisEffectiveClass =
                this instanceof HibernateProxy proxy
                        ? proxy.getHibernateLazyInitializer().getPersistentClass()
                        : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) {
            return false;
        }
        BaseEntity that = (BaseEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }
}

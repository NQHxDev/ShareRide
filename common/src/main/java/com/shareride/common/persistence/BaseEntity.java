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
 * <p>Key architectural design points: 1. Primary key is UUIDv7: Time-ordered UUID for better
 * database index performance and reduced fragmentation. Each entity MUST specify its own column
 * name (e.g., user_id, request_id) for clarity and explicit schema design. 2. Optimistic Locking:
 * Managed via @Version to prevent lost updates in highly concurrent ride-hailing scenarios (e.g.,
 * simultaneous driver acceptance, status updates). 3. Safe equals and hashCode: Robust against
 * Hibernate lazy proxies and unpersisted state.
 *
 * <p>Note: Subclasses MUST override the @Column(name = "xxx_id") annotation on the id field to
 * specify the concrete column name (e.g., user_id, ride_request_id, driver_id).
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity implements Serializable {

    /**
     * UUIDv7 primary key. Subclasses MUST override @Column annotation to specify the concrete
     * column name (e.g., @Column(name = "user_id")).
     */
    @Id
    @Column(updatable = false, nullable = false)
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

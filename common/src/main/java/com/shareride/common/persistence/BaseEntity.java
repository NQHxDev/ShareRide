package com.shareride.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.proxy.HibernateProxy;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Foundational mapped superclass for all ShareRide microservice relational
 * entities.
 * <p>
 * Key architectural design points:
 * 1. Primary key is UUID: Essential for distributed microservices to avoid
 * sequential ID enumeration
 * attacks and to enable client/pre-insert ID assignment without database
 * round-trips.
 * 2. Optimistic Locking: Managed via @Version to prevent lost updates in highly
 * concurrent
 * ride-hailing scenarios (e.g., simultaneous driver acceptance, status
 * updates).
 * 3. Safe equals and hashCode: Robust against Hibernate lazy proxies and
 * unpersisted state.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity implements Serializable {

   @Id
   @Column(name = "id", updatable = false, nullable = false)
   private UUID id;

   @Version
   @Column(name = "version", nullable = false)
   private Long version = 0L;

   @PrePersist
   protected void ensureId() {
      if (this.id == null) {
         this.id = UUID.randomUUID();
      }
   }

   @Override
   public boolean equals(Object o) {
      if (this == o)
         return true;
      if (o == null)
         return false;
      Class<?> oEffectiveClass = o instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : o.getClass();
      Class<?> thisEffectiveClass = this instanceof HibernateProxy proxy
            ? proxy.getHibernateLazyInitializer().getPersistentClass()
            : this.getClass();
      if (thisEffectiveClass != oEffectiveClass)
         return false;
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

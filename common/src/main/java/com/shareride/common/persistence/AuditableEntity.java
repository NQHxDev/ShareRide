package com.shareride.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Enterprise auditing mapped superclass capturing temporal creation and
 * modification timestamps,
 * along with the identity of the user or system component making the changes.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity extends BaseEntity {

   @CreatedDate
   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt;

   @LastModifiedDate
   @Column(name = "updated_at", nullable = false)
   private Instant updatedAt;

   @CreatedBy
   @Column(name = "created_by", updatable = false, length = 64)
   private String createdBy;

   @LastModifiedBy
   @Column(name = "updated_by", length = 64)
   private String updatedBy;

}

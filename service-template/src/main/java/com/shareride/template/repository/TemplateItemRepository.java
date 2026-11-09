package com.shareride.template.repository;

import com.shareride.template.domain.TemplateItem;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data JPA Repository for {@link TemplateItem}. */
@Repository
public interface TemplateItemRepository extends JpaRepository<TemplateItem, UUID> {

    Optional<TemplateItem> findByCodeAndDeletedFalse(String code);

    Optional<TemplateItem> findByIdAndDeletedFalse(UUID id);

    boolean existsByCodeAndDeletedFalse(String code);
}

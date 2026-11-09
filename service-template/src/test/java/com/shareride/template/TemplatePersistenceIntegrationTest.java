package com.shareride.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shareride.common.outbox.OutboxMessage;
import com.shareride.common.outbox.OutboxMessageRepository;
import com.shareride.common.outbox.OutboxStatus;
import com.shareride.template.domain.TemplateItem;
import com.shareride.template.domain.TemplateItemStatus;
import com.shareride.template.dto.CreateTemplateItemRequest;
import com.shareride.template.dto.TemplateItemResponse;
import com.shareride.template.repository.TemplateItemRepository;
import com.shareride.template.service.TemplateService;
import com.shareride.template.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TemplatePersistenceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TemplateItemRepository repository;

    @Autowired private OutboxMessageRepository outboxMessageRepository;

    @Autowired private TemplateService templateService;

    @AfterEach
    void tearDown() {
        repository.deleteAll();
        outboxMessageRepository.deleteAll();
    }

    @Test
    @DisplayName("Should persist TemplateItem in PostgreSQL and record Outbox message")
    void testCreateItemIntegration() {
        CreateTemplateItemRequest request =
                CreateTemplateItemRequest.builder()
                        .name("Airport Flat Rate")
                        .code("AIRPORT_FLAT")
                        .description("Fixed tariff for airport transfer")
                        .build();

        TemplateItemResponse response = templateService.createItem(request);
        assertNotNull(response.getId());
        assertEquals("AIRPORT_FLAT", response.getCode());

        // Verify entity in PostgreSQL
        Optional<TemplateItem> persisted = repository.findById(response.getId());
        assertTrue(persisted.isPresent());
        assertFalse(persisted.get().isDeleted());
        assertEquals(TemplateItemStatus.ACTIVE, persisted.get().getStatus());

        // Verify Transactional Outbox record
        List<OutboxMessage> outboxMessages =
                outboxMessageRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        assertFalse(outboxMessages.isEmpty());
        assertTrue(
                outboxMessages.stream()
                        .anyMatch(msg -> "TEMPLATE_ITEM_CREATED".equals(msg.getEventType())));
    }

    @Test
    @DisplayName("Should execute soft-delete without removing row from database")
    void testSoftDeleteIntegration() {
        CreateTemplateItemRequest request =
                CreateTemplateItemRequest.builder()
                        .name("Night Tariff")
                        .code("NIGHT_TARIFF")
                        .build();

        TemplateItemResponse created = templateService.createItem(request);
        templateService.deleteItem(created.getId(), "admin-user");

        // Should NOT be found via active lookup
        Optional<TemplateItem> active = repository.findByIdAndDeletedFalse(created.getId());
        assertFalse(active.isPresent());

        // Raw row MUST still exist in database with deleted=true
        Optional<TemplateItem> raw = repository.findById(created.getId());
        assertTrue(raw.isPresent());
        assertTrue(raw.get().isDeleted());
        assertEquals("admin-user", raw.get().getDeletedBy());
        assertNotNull(raw.get().getDeletedAt());
    }
}

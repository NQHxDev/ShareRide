package com.shareride.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.common.exception.ConflictException;
import com.shareride.common.exception.ResourceNotFoundException;
import com.shareride.template.domain.TemplateItem;
import com.shareride.template.domain.TemplateItemStatus;
import com.shareride.template.dto.CreateTemplateItemRequest;
import com.shareride.template.dto.TemplateItemResponse;
import com.shareride.template.dto.UpdateTemplateItemRequest;
import com.shareride.template.publisher.TemplateEventPublisher;
import com.shareride.template.repository.TemplateItemRepository;
import com.shareride.template.service.TemplateService;
import com.shareride.template.service.impl.TemplateServiceImpl;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock private TemplateItemRepository repository;

    @Mock private TemplateEventPublisher eventPublisher;

    @Mock
    private ObjectProvider<com.shareride.common.redis.DistributedLockService>
            distributedLockServiceProvider;

    private TemplateService templateService;

    @BeforeEach
    void setUp() {
        templateService =
                new TemplateServiceImpl(repository, eventPublisher, distributedLockServiceProvider);
    }

    @Test
    @DisplayName("createItem should save entity, publish outbox event, and return response")
    void testCreateItemSuccess() {
        CreateTemplateItemRequest request =
                CreateTemplateItemRequest.builder()
                        .name("Dynamic Surge")
                        .code("SURGE_PEAK")
                        .description("Surge pricing factor")
                        .build();

        UUID id = UUID.randomUUID();
        TemplateItem savedItem =
                TemplateItem.builder()
                        .name("Dynamic Surge")
                        .code("SURGE_PEAK")
                        .description("Surge pricing factor")
                        .status(TemplateItemStatus.ACTIVE)
                        .build();
        savedItem.setId(id);
        savedItem.setCreatedAt(Instant.now());
        savedItem.setUpdatedAt(Instant.now());

        when(repository.existsByCodeAndDeletedFalse("SURGE_PEAK")).thenReturn(false);
        when(repository.save(any(TemplateItem.class))).thenReturn(savedItem);

        TemplateItemResponse response = templateService.createItem(request);

        assertNotNull(response);
        assertEquals(id, response.getId());
        assertEquals("SURGE_PEAK", response.getCode());
        verify(repository).save(any(TemplateItem.class));
        verify(eventPublisher).recordItemCreatedOutbox(savedItem);
        verify(eventPublisher).publishItemCreatedDirect(savedItem);
    }

    @Test
    @DisplayName("createItem with existing code should throw ConflictException")
    void testCreateItemDuplicateThrowsConflict() {
        CreateTemplateItemRequest request =
                CreateTemplateItemRequest.builder().name("Duplicate").code("SURGE_PEAK").build();

        when(repository.existsByCodeAndDeletedFalse("SURGE_PEAK")).thenReturn(true);

        assertThrows(ConflictException.class, () -> templateService.createItem(request));
    }

    @Test
    @DisplayName("getItemById should return item if present")
    void testGetItemByIdSuccess() {
        UUID id = UUID.randomUUID();
        TemplateItem item =
                TemplateItem.builder()
                        .name("Item 1")
                        .code("ITEM_1")
                        .status(TemplateItemStatus.ACTIVE)
                        .build();
        item.setId(id);

        when(repository.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(item));

        TemplateItemResponse response = templateService.getItemById(id);
        assertNotNull(response);
        assertEquals(id, response.getId());
    }

    @Test
    @DisplayName("getItemById should throw ResourceNotFoundException if absent")
    void testGetItemByIdNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndDeletedFalse(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> templateService.getItemById(id));
    }

    @Test
    @DisplayName("updateItem should update fields and record outbox event")
    void testUpdateItemSuccess() {
        UUID id = UUID.randomUUID();
        TemplateItem existing =
                TemplateItem.builder()
                        .name("Old Name")
                        .code("ITEM_1")
                        .status(TemplateItemStatus.ACTIVE)
                        .build();
        existing.setId(id);

        UpdateTemplateItemRequest updateRequest =
                UpdateTemplateItemRequest.builder()
                        .name("New Name")
                        .status(TemplateItemStatus.INACTIVE)
                        .build();

        when(repository.findByIdAndDeletedFalse(id)).thenReturn(Optional.of(existing));
        when(repository.save(any(TemplateItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TemplateItemResponse response = templateService.updateItem(id, updateRequest);
        assertEquals("New Name", response.getName());
        assertEquals(TemplateItemStatus.INACTIVE, response.getStatus());
        verify(eventPublisher).recordItemUpdatedOutbox(existing);
    }
}

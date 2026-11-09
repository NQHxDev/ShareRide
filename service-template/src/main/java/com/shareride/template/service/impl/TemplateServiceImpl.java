package com.shareride.template.service.impl;

import com.shareride.common.cache.DistributedLockService;
import com.shareride.common.exception.ConflictException;
import com.shareride.common.exception.ResourceNotFoundException;
import com.shareride.template.constant.TemplateConstants;
import com.shareride.template.constant.TemplateMessage;
import com.shareride.template.constant.TemplateResponseCode;
import com.shareride.template.domain.TemplateItem;
import com.shareride.template.domain.TemplateItemStatus;
import com.shareride.template.dto.CreateTemplateItemRequest;
import com.shareride.template.dto.TemplateItemResponse;
import com.shareride.template.dto.UpdateTemplateItemRequest;
import com.shareride.template.publisher.TemplateEventPublisher;
import com.shareride.template.repository.TemplateItemRepository;
import com.shareride.template.service.TemplateService;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Enterprise Service Implementation for {@link TemplateService}. */
@Slf4j
@Service
public class TemplateServiceImpl implements TemplateService {

    private final TemplateItemRepository repository;
    private final TemplateEventPublisher eventPublisher;
    private final DistributedLockService distributedLockService;

    public TemplateServiceImpl(
            TemplateItemRepository repository,
            TemplateEventPublisher eventPublisher,
            ObjectProvider<DistributedLockService> distributedLockServiceProvider) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.distributedLockService = distributedLockServiceProvider.getIfAvailable();
    }

    @Override
    @Transactional
    public TemplateItemResponse createItem(CreateTemplateItemRequest request) {
        String lockKey =
                TemplateConstants.RedisLock.TEMPLATE_ITEM_LOCK_PREFIX
                        + request.getCode().toLowerCase().trim();

        if (distributedLockService != null) {
            return distributedLockService.executeWithLock(
                    lockKey,
                    TemplateConstants.RedisLock.DEFAULT_LOCK_TIMEOUT,
                    () -> doCreateItem(request));
        }
        return doCreateItem(request);
    }

    private TemplateItemResponse doCreateItem(CreateTemplateItemRequest request) {
        String normalizedCode = request.getCode().trim();

        if (repository.existsByCodeAndDeletedFalse(normalizedCode)) {
            throw new ConflictException(
                    TemplateResponseCode.DUPLICATE_TEMPLATE_CODE,
                    TemplateMessage.duplicateCode(normalizedCode));
        }

        TemplateItem item =
                TemplateItem.builder()
                        .name(request.getName().trim())
                        .code(normalizedCode)
                        .description(request.getDescription())
                        .status(TemplateItemStatus.ACTIVE)
                        .build();

        TemplateItem savedItem = repository.save(item);

        // Record Transactional Outbox event (guarantees dual-write consistency with PostgreSQL)
        eventPublisher.recordItemCreatedOutbox(savedItem);

        // Optional non-blocking direct broadcast over RabbitMQ
        eventPublisher.publishItemCreatedDirect(savedItem);

        log.info(
                "Successfully created TemplateItem [{}] with code [{}]",
                savedItem.getId(),
                savedItem.getCode());
        return toResponse(savedItem);
    }

    @Override
    @Transactional(readOnly = true)
    public TemplateItemResponse getItemById(UUID id) {
        TemplateItem item =
                repository
                        .findByIdAndDeletedFalse(id)
                        .orElseThrow(() -> new ResourceNotFoundException("TemplateItem", id));
        return toResponse(item);
    }

    @Override
    @Transactional(readOnly = true)
    public TemplateItemResponse getItemByCode(String code) {
        TemplateItem item =
                repository
                        .findByCodeAndDeletedFalse(code)
                        .orElseThrow(() -> new ResourceNotFoundException("TemplateItem", code));
        return toResponse(item);
    }

    @Override
    @Transactional
    public TemplateItemResponse updateItem(UUID id, UpdateTemplateItemRequest request) {
        TemplateItem item =
                repository
                        .findByIdAndDeletedFalse(id)
                        .orElseThrow(() -> new ResourceNotFoundException("TemplateItem", id));

        if (request.getName() != null && !request.getName().isBlank()) {
            item.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            item.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            item.setStatus(request.getStatus());
        }

        TemplateItem updatedItem = repository.save(item);
        eventPublisher.recordItemUpdatedOutbox(updatedItem);

        log.info("Successfully updated TemplateItem [{}]", updatedItem.getId());
        return toResponse(updatedItem);
    }

    @Override
    @Transactional
    public void deleteItem(UUID id, String deletedBy) {
        TemplateItem item =
                repository
                        .findByIdAndDeletedFalse(id)
                        .orElseThrow(() -> new ResourceNotFoundException("TemplateItem", id));

        item.markDeleted(deletedBy);
        repository.save(item);

        eventPublisher.recordItemDeletedOutbox(id, item.getCode());
        log.info("Successfully soft-deleted TemplateItem [{}] by [{}]", id, deletedBy);
    }

    private TemplateItemResponse toResponse(TemplateItem item) {
        return TemplateItemResponse.builder()
                .id(item.getId())
                .version(item.getVersion())
                .name(item.getName())
                .code(item.getCode())
                .description(item.getDescription())
                .status(item.getStatus())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .createdBy(item.getCreatedBy())
                .updatedBy(item.getUpdatedBy())
                .build();
    }
}

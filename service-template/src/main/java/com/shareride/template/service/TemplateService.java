package com.shareride.template.service;

import com.shareride.template.dto.CreateTemplateItemRequest;
import com.shareride.template.dto.TemplateItemResponse;
import com.shareride.template.dto.UpdateTemplateItemRequest;
import java.util.UUID;

/**
 * Enterprise Service Interface for Template Item domain operations. Must be implemented by {@link
 * com.shareride.template.service.impl.TemplateServiceImpl}.
 */
public interface TemplateService {

    /**
     * Creates a new template item. Protected by Redis Distributed Lock against duplicate codes,
     * persists entity and records Outbox event.
     *
     * @param request creation payload
     * @return created template item response
     */
    TemplateItemResponse createItem(CreateTemplateItemRequest request);

    /**
     * Retrieves a template item by its UUID identifier.
     *
     * @param id item UUID
     * @return item details
     */
    TemplateItemResponse getItemById(UUID id);

    /**
     * Retrieves a template item by its unique business code.
     *
     * @param code item code
     * @return item details
     */
    TemplateItemResponse getItemByCode(String code);

    /**
     * Updates an existing template item.
     *
     * @param id item UUID
     * @param request update payload
     * @return updated item details
     */
    TemplateItemResponse updateItem(UUID id, UpdateTemplateItemRequest request);

    /**
     * Soft-deletes a template item and records a deletion event in Transactional Outbox.
     *
     * @param id item UUID
     * @param deletedBy identifier of user/admin performing deletion
     */
    void deleteItem(UUID id, String deletedBy);
}

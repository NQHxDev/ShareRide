package com.shareride.template.constant;

/** Standard user-facing messages and templates for Template Service. */
public final class TemplateMessage {

    private TemplateMessage() {
        // Prevent instantiation
    }

    public static final String ITEM_CREATED_SUCCESS = "Template item created successfully";
    public static final String ITEM_UPDATED_SUCCESS = "Template item updated successfully";
    public static final String ITEM_DELETED_SUCCESS = "Template item deleted successfully";

    public static String itemNotFound(Object id) {
        return "Template item not found with id: " + id;
    }

    public static String duplicateCode(String code) {
        return "Template item code already exists: " + code;
    }
}

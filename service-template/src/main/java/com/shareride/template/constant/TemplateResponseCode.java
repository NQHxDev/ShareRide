package com.shareride.template.constant;

/**
 * Business response codes and error codes for Template Service. Bundled inside BaseResponse.code
 * for Frontend/Client consumption.
 */
public final class TemplateResponseCode {

    private TemplateResponseCode() {
        // Prevent instantiation
    }

    public static final String TEMPLATE_ITEM_CREATED = "TEMPLATE_ITEM_CREATED";
    public static final String TEMPLATE_ITEM_UPDATED = "TEMPLATE_ITEM_UPDATED";
    public static final String TEMPLATE_ITEM_DELETED = "TEMPLATE_ITEM_DELETED";
    public static final String TEMPLATE_ITEM_NOT_FOUND = "TEMPLATE_ITEM_NOT_FOUND";
    public static final String DUPLICATE_TEMPLATE_CODE = "DUPLICATE_TEMPLATE_CODE";
}

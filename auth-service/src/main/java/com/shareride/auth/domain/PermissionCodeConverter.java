package com.shareride.auth.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** JPA Converter cho PermissionCode để lưu trữ chuỗi dạng 'resource:action' trong database */
@Converter(autoApply = true)
public class PermissionCodeConverter implements AttributeConverter<PermissionCode, String> {

    @Override
    public String convertToDatabaseColumn(PermissionCode attribute) {
        return attribute != null ? attribute.getValue() : null;
    }

    @Override
    public PermissionCode convertToEntityAttribute(String dbData) {
        return dbData != null ? PermissionCode.fromValue(dbData) : null;
    }
}

package com.shareride.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

/** Details of a validation or field-specific error, supporting nested hierarchy. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FieldErrorDetail implements Serializable {

    private String field;
    private Object rejectedValue;
    private String message;
    private String code;

    @Singular("nestedError")
    private List<FieldErrorDetail> nestedErrors;

    public static FieldErrorDetail of(String field, String message) {
        return FieldErrorDetail.builder().field(field).message(message).build();
    }

    public static FieldErrorDetail of(String field, Object rejectedValue, String message) {
        return FieldErrorDetail.builder()
                .field(field)
                .rejectedValue(rejectedValue)
                .message(message)
                .build();
    }

    public static FieldErrorDetail of(
            String field, Object rejectedValue, String message, String code) {
        return FieldErrorDetail.builder()
                .field(field)
                .rejectedValue(rejectedValue)
                .message(message)
                .code(code)
                .build();
    }

    public static FieldErrorDetail of(
            String field, String message, List<FieldErrorDetail> nestedErrors) {
        return FieldErrorDetail.builder()
                .field(field)
                .message(message)
                .nestedErrors(nestedErrors)
                .build();
    }

    public FieldErrorDetail addNestedError(FieldErrorDetail error) {
        if (this.nestedErrors == null) {
            this.nestedErrors = new ArrayList<>();
        }
        this.nestedErrors.add(error);
        return this;
    }

    public boolean hasNestedErrors() {
        return this.nestedErrors != null && !this.nestedErrors.isEmpty();
    }

    /** Extract list of FieldErrorDetail from Spring BindingResult (both field & global errors). */
    public static List<FieldErrorDetail> fromBindingResult(BindingResult bindingResult) {
        if (bindingResult == null) {
            return List.of();
        }

        List<FieldErrorDetail> list = new ArrayList<>();
        for (FieldError fieldError : bindingResult.getFieldErrors()) {
            list.add(
                    FieldErrorDetail.builder()
                            .field(fieldError.getField())
                            .rejectedValue(fieldError.getRejectedValue())
                            .code(fieldError.getCode())
                            .message(fieldError.getDefaultMessage())
                            .build());
        }

        for (ObjectError globalError : bindingResult.getGlobalErrors()) {
            list.add(
                    FieldErrorDetail.builder()
                            .field(globalError.getObjectName())
                            .code(globalError.getCode())
                            .message(globalError.getDefaultMessage())
                            .build());
        }

        return list;
    }

    /**
     * Transforms a flat list of errors with dotted field names (e.g. "driver.license.number") into
     * a hierarchical tree with nestedErrors.
     */
    public static List<FieldErrorDetail> buildHierarchicalErrors(
            List<FieldErrorDetail> flatErrors) {
        if (flatErrors == null || flatErrors.isEmpty()) {
            return List.of();
        }

        Map<String, List<FieldErrorDetail>> grouped = new LinkedHashMap<>();
        List<FieldErrorDetail> result = new ArrayList<>();

        for (FieldErrorDetail err : flatErrors) {
            String field = err.getField();
            if (field != null && field.contains(".")) {
                int dotIdx = field.indexOf('.');
                String parent = field.substring(0, dotIdx);
                String child = field.substring(dotIdx + 1);

                FieldErrorDetail childError =
                        FieldErrorDetail.builder()
                                .field(child)
                                .rejectedValue(err.getRejectedValue())
                                .code(err.getCode())
                                .message(err.getMessage())
                                .nestedErrors(err.getNestedErrors())
                                .build();

                grouped.computeIfAbsent(parent, k -> new ArrayList<>()).add(childError);
            } else {
                result.add(err);
            }
        }

        for (Map.Entry<String, List<FieldErrorDetail>> entry : grouped.entrySet()) {
            List<FieldErrorDetail> nested = buildHierarchicalErrors(entry.getValue());
            result.add(
                    FieldErrorDetail.builder()
                            .field(entry.getKey())
                            .message(null)
                            .nestedErrors(nested)
                            .build());
        }

        return result;
    }
}

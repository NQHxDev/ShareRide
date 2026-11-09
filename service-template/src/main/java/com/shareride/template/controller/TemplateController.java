package com.shareride.template.controller;

import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.response.BaseResponse;
import com.shareride.common.versioning.ApiVersion;
import com.shareride.template.constant.TemplateConstants;
import com.shareride.template.constant.TemplateMessage;
import com.shareride.template.constant.TemplateResponseCode;
import com.shareride.template.dto.CreateTemplateItemRequest;
import com.shareride.template.dto.TemplateItemResponse;
import com.shareride.template.dto.UpdateTemplateItemRequest;
import com.shareride.template.service.TemplateService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller exposing Template Item APIs. Follows ShareRide Enterprise API standards:
 *
 * <ul>
 *   <li>Versioned via @ApiVersion("v1")
 *   <li>Enclosed in BaseResponse&lt;T&gt; wrapper
 *   <li>Input validation with @Valid
 * </ul>
 */
@RestController
@RequestMapping(TemplateConstants.Endpoint.BASE_PATH)
@ApiVersion("v1")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @PostMapping
    public ResponseEntity<BaseResponse<TemplateItemResponse>> createItem(
            @Valid @RequestBody CreateTemplateItemRequest request) {
        TemplateItemResponse response = templateService.createItem(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        BaseResponse.success(
                                TemplateResponseCode.TEMPLATE_ITEM_CREATED,
                                TemplateMessage.ITEM_CREATED_SUCCESS,
                                response));
    }

    @GetMapping(TemplateConstants.Endpoint.BY_ID)
    public ResponseEntity<BaseResponse<TemplateItemResponse>> getItemById(
            @PathVariable(TemplateConstants.PATH_VARIABLE_ID) UUID id) {
        TemplateItemResponse response = templateService.getItemById(id);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<BaseResponse<TemplateItemResponse>> getItemByCode(
            @PathVariable("code") String code) {
        TemplateItemResponse response = templateService.getItemByCode(code);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    @PutMapping(TemplateConstants.Endpoint.BY_ID)
    public ResponseEntity<BaseResponse<TemplateItemResponse>> updateItem(
            @PathVariable(TemplateConstants.PATH_VARIABLE_ID) UUID id,
            @Valid @RequestBody UpdateTemplateItemRequest request) {
        TemplateItemResponse response = templateService.updateItem(id, request);
        return ResponseEntity.ok(
                BaseResponse.success(
                        TemplateResponseCode.TEMPLATE_ITEM_UPDATED,
                        TemplateMessage.ITEM_UPDATED_SUCCESS,
                        response));
    }

    @DeleteMapping(TemplateConstants.Endpoint.BY_ID)
    public ResponseEntity<BaseResponse<Void>> deleteItem(
            @PathVariable(TemplateConstants.PATH_VARIABLE_ID) UUID id) {
        UserContext context = UserContextHolder.getContext();
        String deletedBy =
                (context != null && context.getUserId() != null) ? context.getUserId() : "SYSTEM";
        templateService.deleteItem(id, deletedBy);
        return ResponseEntity.ok(BaseResponse.emptySuccess());
    }
}

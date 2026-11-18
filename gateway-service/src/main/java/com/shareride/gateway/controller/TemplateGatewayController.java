package com.shareride.gateway.controller;

import com.shareride.common.grpc.template.v1.GetTemplateRequest;
import com.shareride.common.grpc.template.v1.GetTemplateResponse;
import com.shareride.common.grpc.template.v1.VerifyTemplateRequest;
import com.shareride.common.grpc.template.v1.VerifyTemplateResponse;
import com.shareride.common.response.BaseResponse;
import com.shareride.gateway.client.TemplateServiceGrpcClient;
import com.shareride.gateway.dto.VerifyTemplateRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Northbound Gateway REST controller translating HTTP/REST requests to gRPC calls to Template
 * Service. Implements transparent protocol translation per Phase 3 User Story 1 (T023).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
@Tag(name = "Templates", description = "Template management")
public class TemplateGatewayController {

    private final TemplateServiceGrpcClient templateServiceGrpcClient;

    @Operation(summary = "Get template by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Template retrieved"),
        @ApiResponse(responseCode = "404", description = "Template not found")
    })
    @GetMapping("/{templateId}")
    public Mono<ResponseEntity<BaseResponse<GetTemplateResponse>>> getTemplate(
            @PathVariable String templateId,
            @RequestParam(required = false, defaultValue = "en_US") String locale) {
        GetTemplateRequest grpcRequest =
                GetTemplateRequest.newBuilder().setTemplateId(templateId).setLocale(locale).build();

        return templateServiceGrpcClient
                .getTemplate(grpcRequest)
                .map(response -> ResponseEntity.ok(BaseResponse.success(response)))
                .doOnSuccess(r -> log.info("GetTemplate successful: {}", templateId));
    }

    @Operation(summary = "Verify template checksum")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Template verified"),
        @ApiResponse(responseCode = "400", description = "Invalid checksum")
    })
    @PostMapping("/{templateId}/verify")
    public Mono<ResponseEntity<BaseResponse<VerifyTemplateResponse>>> verifyTemplate(
            @PathVariable String templateId, @RequestBody VerifyTemplateRequestDto dto) {
        VerifyTemplateRequest grpcRequest =
                VerifyTemplateRequest.newBuilder()
                        .setTemplateId(templateId)
                        .setChecksum(dto.getChecksum())
                        .build();

        return templateServiceGrpcClient
                .verifyTemplate(grpcRequest)
                .map(response -> ResponseEntity.ok(BaseResponse.success(response)))
                .doOnSuccess(
                        r ->
                                log.info(
                                        "VerifyTemplate: templateId={}, isValid={}",
                                        templateId,
                                        r.getBody().getData().getIsValid()));
    }
}

package com.shareride.gateway.client;

import com.shareride.common.communication.grpc.client.GrpcChannelFactory;
import com.shareride.common.communication.grpc.mapper.GrpcStatusExceptionMapper;
import com.shareride.common.grpc.template.v1.GetTemplateRequest;
import com.shareride.common.grpc.template.v1.GetTemplateResponse;
import com.shareride.common.grpc.template.v1.TemplateHealthCheckRequest;
import com.shareride.common.grpc.template.v1.TemplateHealthCheckResponse;
import com.shareride.common.grpc.template.v1.TemplateServiceGrpc;
import com.shareride.common.grpc.template.v1.VerifyTemplateRequest;
import com.shareride.common.grpc.template.v1.VerifyTemplateResponse;
import io.grpc.Channel;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Reactive gRPC client for Template Service wrapping TemplateServiceBlockingStub. Schedules
 * blocking gRPC calls on Schedulers.boundedElastic() to prevent blocking the event loop and maps
 * gRPC StatusRuntimeException to domain exceptions via GrpcStatusExceptionMapper.
 *
 * <p>All operations return Mono for reactive composition in Gateway's WebFlux handlers.
 */
@Slf4j
@Component
public class TemplateServiceGrpcClient {

    private final TemplateServiceGrpc.TemplateServiceBlockingStub blockingStub;

    public TemplateServiceGrpcClient(GrpcChannelFactory channelFactory) {
        Channel channel = channelFactory.getChannel("template-service");
        this.blockingStub = TemplateServiceGrpc.newBlockingStub(channel);
        log.info("TemplateServiceGrpcClient initialized with channel to template-service");
    }

    /**
     * Get template by ID and locale.
     *
     * @param request the get template request
     * @return Mono emitting GetTemplateResponse or error
     */
    public Mono<GetTemplateResponse> getTemplate(GetTemplateRequest request) {
        return Mono.fromCallable(() -> blockingStub.getTemplate(request))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(
                        StatusRuntimeException.class, GrpcStatusExceptionMapper::toBaseException)
                .doOnSuccess(
                        response ->
                                log.debug(
                                        "GetTemplate successful: templateId={}, version={}",
                                        response.getTemplateId(),
                                        response.getVersion()))
                .doOnError(e -> log.error("GetTemplate failed: {}", e.getMessage()));
    }

    /**
     * Verify template checksum.
     *
     * @param request the verify template request
     * @return Mono emitting VerifyTemplateResponse or error
     */
    public Mono<VerifyTemplateResponse> verifyTemplate(VerifyTemplateRequest request) {
        return Mono.fromCallable(() -> blockingStub.verifyTemplate(request))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(
                        StatusRuntimeException.class, GrpcStatusExceptionMapper::toBaseException)
                .doOnSuccess(
                        response ->
                                log.debug(
                                        "VerifyTemplate successful: templateId={}, isValid={}",
                                        request.getTemplateId(),
                                        response.getIsValid()))
                .doOnError(e -> log.error("VerifyTemplate failed: {}", e.getMessage()));
    }

    /**
     * Health check for Template Service.
     *
     * @param request the health check request (empty)
     * @return Mono emitting TemplateHealthCheckResponse or error
     */
    public Mono<TemplateHealthCheckResponse> healthCheck(TemplateHealthCheckRequest request) {
        return Mono.fromCallable(() -> blockingStub.healthCheck(request))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(
                        StatusRuntimeException.class, GrpcStatusExceptionMapper::toBaseException)
                .doOnSuccess(
                        response ->
                                log.debug(
                                        "HealthCheck successful: status={}, uptime={}s",
                                        response.getStatus(),
                                        response.getUptimeSeconds()))
                .doOnError(e -> log.error("HealthCheck failed: {}", e.getMessage()));
    }
}

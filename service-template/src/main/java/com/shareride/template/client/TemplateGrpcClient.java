package com.shareride.template.client;

import com.shareride.common.communication.grpc.client.GrpcChannelFactory;
import com.shareride.common.communication.grpc.mapper.GrpcStatusExceptionMapper;
import com.shareride.common.resilience.executor.ResilientCallExecutor;
import io.grpc.Channel;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Enterprise standard gRPC Client Wrapper. Provides all essential calling patterns so microservices
 * never have to re-implement boilerplate:
 *
 * <ul>
 *   <li>Managed Channel retrieval with automatic client tracing, dead-line, and logging
 *       interceptors.
 *   <li>Automatic translation between gRPC StatusRuntimeException and ShareRide BaseException.
 *   <li>Resilience integration (Circuit Breaker, Exponential Retry, and Timeout).
 *   <li>Asynchronous non-blocking invocation.
 * </ul>
 */
@Slf4j
@Component
public class TemplateGrpcClient {

    private final GrpcChannelFactory grpcChannelFactory;
    private final ResilientCallExecutor resilientCallExecutor;

    public TemplateGrpcClient(
            GrpcChannelFactory grpcChannelFactory,
            ObjectProvider<ResilientCallExecutor> resilientCallExecutorProvider) {
        this.grpcChannelFactory = grpcChannelFactory;
        this.resilientCallExecutor =
                resilientCallExecutorProvider.getIfAvailable(ResilientCallExecutor::new);
    }

    /**
     * Gets or creates a cached, interceptor-enhanced gRPC Channel for the target service.
     *
     * @param targetServiceName name of the target service (e.g., "auth-service", "ride-service")
     * @return resilient gRPC Channel
     */
    public Channel getChannel(String targetServiceName) {
        return grpcChannelFactory.getChannel(targetServiceName);
    }

    /**
     * Executes a synchronous gRPC call, automatically converting any gRPC {@link
     * StatusRuntimeException} into a standard ShareRide {@link
     * com.shareride.common.exception.BaseException}.
     *
     * @param targetServiceName target microservice identifier
     * @param callFunc function accepting Channel and returning response from gRPC blocking stub
     * @param <R> response type
     * @return response payload
     */
    public <R> R executeCall(String targetServiceName, Function<Channel, R> callFunc) {
        Channel channel = getChannel(targetServiceName);
        try {
            return callFunc.apply(channel);
        } catch (StatusRuntimeException e) {
            log.error(
                    "gRPC call to service [{}] failed with status [{}]: {}",
                    targetServiceName,
                    e.getStatus().getCode(),
                    e.getMessage());
            throw GrpcStatusExceptionMapper.toBaseException(e);
        }
    }

    /**
     * Executes a synchronous gRPC call protected by Circuit Breaker and Exponential Backoff Retry.
     *
     * @param targetServiceName target microservice identifier
     * @param operationName operation name for circuit breaker metric grouping
     * @param callFunc function accepting Channel and returning response
     * @param <R> response type
     * @return response payload
     */
    public <R> R executeResilientCall(
            String targetServiceName, String operationName, Function<Channel, R> callFunc) {
        return resilientCallExecutor.execute(
                targetServiceName + ":" + operationName,
                () -> executeCall(targetServiceName, callFunc));
    }

    /**
     * Executes an asynchronous non-blocking gRPC call.
     *
     * @param targetServiceName target microservice identifier
     * @param callFunc function accepting Channel and returning CompletableFuture
     * @param <R> response type
     * @return CompletableFuture completing with response payload
     */
    public <R> CompletableFuture<R> executeAsyncCall(
            String targetServiceName, Function<Channel, CompletableFuture<R>> callFunc) {
        Channel channel = getChannel(targetServiceName);
        return callFunc.apply(channel)
                .exceptionally(
                        throwable -> {
                            if (throwable instanceof StatusRuntimeException sre) {
                                throw GrpcStatusExceptionMapper.toBaseException(sre);
                            }
                            if (throwable.getCause() instanceof StatusRuntimeException sre) {
                                throw GrpcStatusExceptionMapper.toBaseException(sre);
                            }
                            throw new RuntimeException(throwable);
                        });
    }
}

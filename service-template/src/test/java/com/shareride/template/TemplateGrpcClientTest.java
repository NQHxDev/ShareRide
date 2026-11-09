package com.shareride.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareride.common.communication.grpc.client.GrpcChannelFactory;
import com.shareride.common.exception.BaseException;
import com.shareride.common.exception.ResourceNotFoundException;
import com.shareride.template.client.TemplateGrpcClient;
import io.grpc.Channel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class TemplateGrpcClientTest {

    @Mock private GrpcChannelFactory grpcChannelFactory;

    @Mock private Channel mockChannel;

    @Mock
    private ObjectProvider<com.shareride.common.resilience.executor.ResilientCallExecutor>
            resilientCallExecutorProvider;

    private TemplateGrpcClient grpcClient;

    @BeforeEach
    void setUp() {
        grpcClient = new TemplateGrpcClient(grpcChannelFactory, resilientCallExecutorProvider);
    }

    @Test
    @DisplayName("executeCall should retrieve channel and invoke function")
    void testExecuteCallSuccess() {
        when(grpcChannelFactory.getChannel("auth-service")).thenReturn(mockChannel);

        String result = grpcClient.executeCall("auth-service", channel -> "pong");
        assertEquals("pong", result);
        verify(grpcChannelFactory).getChannel("auth-service");
    }

    @Test
    @DisplayName("executeCall should map StatusRuntimeException to BaseException")
    void testExecuteCallTranslatesException() {
        when(grpcChannelFactory.getChannel("auth-service")).thenReturn(mockChannel);

        StatusRuntimeException grpcEx =
                new StatusRuntimeException(Status.NOT_FOUND.withDescription("User not found"));

        BaseException thrown =
                assertThrows(
                        ResourceNotFoundException.class,
                        () ->
                                grpcClient.executeCall(
                                        "auth-service",
                                        channel -> {
                                            throw grpcEx;
                                        }));

        assertNotNull(thrown);
    }
}

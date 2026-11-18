package com.shareride.common.communication.grpc.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrpcChannelFactoryTest {

    private GrpcClientProperties properties;
    private GrpcChannelFactory factory;

    @BeforeEach
    void setUp() {
        properties = new GrpcClientProperties();
        properties.setDefaultDeadlineMs(5000L);
        properties.setKeepAliveTimeSeconds(60);
        properties.setKeepAliveTimeoutSeconds(20);
        properties.setMaxInboundMessageSize(10485760);
        properties.setEnableRetry(true);
        properties.setMaxRetryAttempts(3);

        factory = new GrpcChannelFactory(properties);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    void testGetChannelByTargetString() {
        Channel channel1 = factory.getChannel("auth-service");
        Channel channel2 = factory.getChannel("auth-service");

        assertThat(channel1).isNotNull();
        assertThat(channel1).isInstanceOf(ManagedChannel.class);
        assertThat(channel1).isSameAs(channel2);
    }

    @Test
    void testGetChannelByHostPortString() {
        Channel channel1 = factory.getChannel("auth-service:9091");
        Channel channel2 = factory.getChannel("auth-service", 9091);

        assertThat(channel1).isNotNull();
        assertThat(channel1).isSameAs(channel2);
    }

    @Test
    void testGetChannelCreatesNewChannelForFirstRequest() {
        Channel channel = factory.getChannel("localhost", 9090);

        assertThat(channel).isNotNull();
        assertThat(channel).isInstanceOf(ManagedChannel.class);
    }

    @Test
    void testGetChannelReturnsCachedChannelForSameTarget() {
        Channel channel1 = factory.getChannel("localhost", 9090);
        Channel channel2 = factory.getChannel("localhost", 9090);

        assertThat(channel1).isSameAs(channel2);
    }

    @Test
    void testGetChannelCreatesDistinctChannelsForDifferentTargets() {
        Channel channel1 = factory.getChannel("localhost", 9090);
        Channel channel2 = factory.getChannel("localhost", 9091);
        Channel channel3 = factory.getChannel("auth-service", 9090);

        assertThat(channel1).isNotSameAs(channel2);
        assertThat(channel1).isNotSameAs(channel3);
        assertThat(channel2).isNotSameAs(channel3);
    }

    @Test
    void testGetManagedChannelReturnsCorrectType() {
        ManagedChannel channel = factory.getManagedChannel("localhost", 9090);

        assertThat(channel).isNotNull();
        assertThat(channel).isInstanceOf(ManagedChannel.class);
    }

    @Test
    void testGetManagedChannelReturnsCachedInstance() {
        ManagedChannel channel1 = factory.getManagedChannel("localhost", 9090);
        ManagedChannel channel2 = factory.getManagedChannel("localhost", 9090);

        assertThat(channel1).isSameAs(channel2);
    }

    @Test
    void testAddGlobalInterceptorRegistersInterceptor() {
        ClientInterceptor mockInterceptor =
                new ClientInterceptor() {
                    @Override
                    public <ReqT, RespT> io.grpc.ClientCall<ReqT, RespT> interceptCall(
                            io.grpc.MethodDescriptor<ReqT, RespT> method,
                            io.grpc.CallOptions callOptions,
                            io.grpc.Channel next) {
                        return next.newCall(method, callOptions);
                    }
                };

        factory.addGlobalInterceptor(mockInterceptor);
        Channel channel = factory.getChannel("localhost", 9090);

        assertThat(channel).isNotNull();
    }

    @Test
    void testAddGlobalInterceptorIgnoresNullInterceptor() {
        factory.addGlobalInterceptor(null);
        Channel channel = factory.getChannel("localhost", 9090);

        assertThat(channel).isNotNull();
    }

    @Test
    void testDestroyShutdownsAllCachedChannels() throws Exception {
        ManagedChannel channel1 = factory.getManagedChannel("localhost", 9090);
        ManagedChannel channel2 = factory.getManagedChannel("localhost", 9091);
        ManagedChannel channel3 = factory.getManagedChannel("auth-service", 9090);

        factory.destroy();

        assertThat(channel1.isShutdown()).isTrue();
        assertThat(channel2.isShutdown()).isTrue();
        assertThat(channel3.isShutdown()).isTrue();
    }

    @Test
    void testDestroyWaitsForGracefulTermination() throws Exception {
        ManagedChannel channel = factory.getManagedChannel("localhost", 9090);

        factory.destroy();

        assertThat(channel.isShutdown()).isTrue();
        assertThat(channel.isTerminated()).isTrue();
    }

    @Test
    void testConcurrentAccessToSameTarget() throws Exception {
        Thread thread1 =
                new Thread(
                        () -> {
                            for (int i = 0; i < 100; i++) {
                                factory.getChannel("localhost", 9090);
                            }
                        });

        Thread thread2 =
                new Thread(
                        () -> {
                            for (int i = 0; i < 100; i++) {
                                factory.getChannel("localhost", 9090);
                            }
                        });

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        Channel channel = factory.getChannel("localhost", 9090);
        assertThat(channel).isNotNull();
    }
}

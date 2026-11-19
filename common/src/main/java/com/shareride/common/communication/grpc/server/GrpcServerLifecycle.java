package com.shareride.common.communication.grpc.server;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptor;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.protobuf.services.ProtoReflectionService;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;

/**
 * Manages the gRPC server lifecycle as a Spring {@link SmartLifecycle} component. Discovers all
 * Spring beans annotated with {@link GrpcService}, discovers all {@link ServerInterceptor} beans,
 * configures a Netty-based gRPC server with interceptors in order (Trace -> Logging -> Exception),
 * registers health checks and reflection services, and handles graceful shutdown.
 *
 * <p>The server starts on phase {@code Integer.MAX_VALUE - 100} to ensure it initializes after most
 * other Spring components but before the application is fully ready.
 */
@Slf4j
@RequiredArgsConstructor
public class GrpcServerLifecycle implements SmartLifecycle {

    private final ApplicationContext applicationContext;
    private final GrpcServerProperties properties;
    private Server server;
    private volatile boolean running = false;
    private final HealthStatusManager healthStatusManager = new HealthStatusManager();

    @Override
    public void start() {
        if (!properties.isEnabled()) {
            log.info("gRPC server is disabled via configuration");
            return;
        }

        try {
            List<BindableService> services =
                    applicationContext.getBeansWithAnnotation(GrpcService.class).values().stream()
                            .filter(BindableService.class::isInstance)
                            .map(BindableService.class::cast)
                            .toList();

            if (services.isEmpty()) {
                log.warn(
                        "No @GrpcService beans found in application context. gRPC server will not"
                                + " start.");
                return;
            }

            ServerBuilder<?> serverBuilder = ServerBuilder.forPort(properties.getPort());

            serverBuilder
                    .maxInboundMessageSize(properties.getMaxInboundMessageSize())
                    .keepAliveTime(properties.getKeepAliveTimeSeconds(), TimeUnit.SECONDS)
                    .keepAliveTimeout(properties.getKeepAliveTimeoutSeconds(), TimeUnit.SECONDS)
                    .permitKeepAliveTime(
                            properties.getPermitKeepAliveTimeSeconds(), TimeUnit.SECONDS)
                    .permitKeepAliveWithoutCalls(properties.isPermitKeepAliveWithoutCalls())
                    .maxConnectionIdle(properties.getMaxConnectionIdleSeconds(), TimeUnit.SECONDS)
                    .maxConnectionAge(properties.getMaxConnectionAgeSeconds(), TimeUnit.SECONDS)
                    .maxConnectionAgeGrace(
                            properties.getMaxConnectionAgeGraceSeconds(), TimeUnit.SECONDS);

            // Discover and register ServerInterceptor beans in order:
            // Trace -> Logging -> Exception
            List<ServerInterceptor> interceptors =
                    applicationContext.getBeansOfType(ServerInterceptor.class).values().stream()
                            .sorted(this::compareInterceptors)
                            .toList();

            for (ServerInterceptor interceptor : interceptors) {
                serverBuilder.intercept(interceptor);
                log.info(
                        "Registered gRPC server interceptor: {}",
                        interceptor.getClass().getSimpleName());
            }

            for (BindableService service : services) {
                serverBuilder.addService(service);
                log.info(
                        "Registered gRPC service: {}",
                        service.bindService().getServiceDescriptor().getName());
            }

            serverBuilder.addService(healthStatusManager.getHealthService());
            serverBuilder.addService(ProtoReflectionService.newInstance());

            server = serverBuilder.build().start();
            running = true;

            log.info(
                    "gRPC server started successfully on port {} with {} service(s) and {}"
                            + " interceptor(s)",
                    properties.getPort(),
                    services.size(),
                    interceptors.size());

        } catch (IOException e) {
            log.error("Failed to start gRPC server on port {}", properties.getPort(), e);
            throw new IllegalStateException("gRPC server startup failed", e);
        }
    }

    /**
     * Orders interceptors: Trace -> Logging -> Exception. Trace must be first to populate context,
     * Logging second to log with context, Exception last to catch all errors.
     */
    private int compareInterceptors(ServerInterceptor a, ServerInterceptor b) {
        String aName = a.getClass().getSimpleName();
        String bName = b.getClass().getSimpleName();

        if (aName.contains("Trace")) {
            return -1;
        }
        if (bName.contains("Trace")) {
            return 1;
        }

        if (aName.contains("Logging") && !bName.contains("Trace")) {
            return -1;
        }
        if (bName.contains("Logging") && !aName.contains("Trace")) {
            return 1;
        }

        if (aName.contains("Exception")) {
            return 1;
        }
        if (bName.contains("Exception")) {
            return -1;
        }

        return 0;
    }

    @Override
    public void stop() {
        if (server != null && running) {
            log.info(
                    "Initiating gRPC server graceful shutdown (grace period: {}s)",
                    properties.getShutdownGracePeriodSeconds());

            healthStatusManager.enterTerminalState();

            try {
                server.shutdown();
                if (!server.awaitTermination(
                        properties.getShutdownGracePeriodSeconds(), TimeUnit.SECONDS)) {
                    log.warn(
                            "gRPC server did not terminate gracefully within {}s, forcing"
                                    + " shutdown",
                            properties.getShutdownGracePeriodSeconds());
                    server.shutdownNow();
                    server.awaitTermination(5, TimeUnit.SECONDS);
                }
                log.info("gRPC server stopped successfully");
            } catch (InterruptedException e) {
                log.error("gRPC server shutdown interrupted", e);
                server.shutdownNow();
                Thread.currentThread().interrupt();
            } finally {
                running = false;
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    /**
     * Returns the actual port the gRPC server is listening on. This is particularly useful in tests
     * when using dynamic port allocation (port=0).
     *
     * @return the listening port, or -1 if the server is not running
     */
    public int getPort() {
        if (server != null && running) {
            return server.getPort();
        }
        return -1;
    }
}

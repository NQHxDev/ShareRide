package com.shareride.common.communication.grpc.server;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.stereotype.Service;

/**
 * Stereotype annotation marking a Spring bean as a gRPC service implementation. Beans annotated
 * with {@code @GrpcService} are automatically discovered by {@link GrpcServerLifecycle} and
 * registered with the gRPC server at startup.
 *
 * <p>Classes annotated with {@code @GrpcService} MUST extend a generated gRPC {@code *ImplBase}
 * class (e.g., {@code AuthServiceGrpc.AuthServiceImplBase}).
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @GrpcService
 * public class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {
 *     @Override
 *     public void login(LoginRequest request, StreamObserver<LoginResponse> responseObserver) {
 *         // implementation
 *     }
 * }
 * }</pre>
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Service
public @interface GrpcService {}

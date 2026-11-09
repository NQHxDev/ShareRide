package com.shareride.common.communication.grpc;

import io.grpc.Metadata;

/**
 * Standard gRPC Metadata keys for distributed tracing, user identity, tenant isolation, and error
 * propagation.
 */
public final class GrpcMetadataConstants {

    public static final Metadata.Key<String> TRACE_ID_KEY =
            Metadata.Key.of("x-trace-id", Metadata.ASCII_STRING_MARSHALLER);

    public static final Metadata.Key<String> SPAN_ID_KEY =
            Metadata.Key.of("x-span-id", Metadata.ASCII_STRING_MARSHALLER);

    public static final Metadata.Key<String> TRACEPARENT_KEY =
            Metadata.Key.of("traceparent", Metadata.ASCII_STRING_MARSHALLER);

    public static final Metadata.Key<String> USER_ID_KEY =
            Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER);

    public static final Metadata.Key<String> TENANT_ID_KEY =
            Metadata.Key.of("x-tenant-id", Metadata.ASCII_STRING_MARSHALLER);

    public static final Metadata.Key<String> CORRELATION_ID_KEY =
            Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);

    public static final Metadata.Key<String> PRIORITY_KEY =
            Metadata.Key.of("x-priority", Metadata.ASCII_STRING_MARSHALLER);

    public static final Metadata.Key<String> FEATURE_FLAGS_KEY =
            Metadata.Key.of("x-feature-flags", Metadata.ASCII_STRING_MARSHALLER);

    public static final Metadata.Key<String> ERROR_CODE_KEY =
            Metadata.Key.of("x-error-code", Metadata.ASCII_STRING_MARSHALLER);

    private GrpcMetadataConstants() {}
}

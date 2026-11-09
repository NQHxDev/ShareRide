package com.shareride.common.communication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.shareride.common.communication.grpc.GrpcMetadataConstants;
import com.shareride.common.communication.grpc.mapper.GrpcStatusExceptionMapper;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.exception.BadRequestException;
import com.shareride.common.exception.BaseException;
import com.shareride.common.exception.ConflictException;
import com.shareride.common.exception.ForbiddenException;
import com.shareride.common.exception.RateLimitExceededException;
import com.shareride.common.exception.RequestTimeoutException;
import com.shareride.common.exception.ResourceNotFoundException;
import com.shareride.common.exception.UnauthorizedException;
import com.shareride.common.filter.TraceIdContext;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("gRPC Communication & Status Mapping Tests")
class GrpcCommunicationTest {

    @AfterEach
    void tearDown() {
        TraceIdContext.clear();
        UserContextHolder.clear();
    }

    @Test
    @DisplayName("Should translate BaseExceptions to canonical gRPC Status codes")
    void testBaseExceptionToGrpcStatus() {
        assertEquals(
                Status.Code.NOT_FOUND,
                GrpcStatusExceptionMapper.toGrpcStatus(
                                new ResourceNotFoundException("User not found"))
                        .getCode());

        assertEquals(
                Status.Code.UNAUTHENTICATED,
                GrpcStatusExceptionMapper.toGrpcStatus(new UnauthorizedException("Token expired"))
                        .getCode());

        assertEquals(
                Status.Code.INVALID_ARGUMENT,
                GrpcStatusExceptionMapper.toGrpcStatus(new BadRequestException("Invalid payload"))
                        .getCode());

        assertEquals(
                Status.Code.PERMISSION_DENIED,
                GrpcStatusExceptionMapper.toGrpcStatus(new ForbiddenException("Access denied"))
                        .getCode());

        assertEquals(
                Status.Code.ALREADY_EXISTS,
                GrpcStatusExceptionMapper.toGrpcStatus(
                                new ConflictException("Email already registered"))
                        .getCode());

        assertEquals(
                Status.Code.DEADLINE_EXCEEDED,
                GrpcStatusExceptionMapper.toGrpcStatus(
                                new RequestTimeoutException("Call timed out"))
                        .getCode());

        assertEquals(
                Status.Code.RESOURCE_EXHAUSTED,
                GrpcStatusExceptionMapper.toGrpcStatus(
                                new RateLimitExceededException("Quota exceeded"))
                        .getCode());
    }

    @Test
    @DisplayName("Should translate gRPC StatusRuntimeException back to ShareRide BaseExceptions")
    void testGrpcStatusToBaseException() {
        Metadata trailers = new Metadata();
        trailers.put(GrpcMetadataConstants.ERROR_CODE_KEY, "USER_NOT_FOUND");

        StatusRuntimeException notFoundEx =
                Status.NOT_FOUND
                        .withDescription("Driver #123 not found")
                        .asRuntimeException(trailers);

        BaseException mappedNotFound = GrpcStatusExceptionMapper.toBaseException(notFoundEx);
        assertInstanceOf(ResourceNotFoundException.class, mappedNotFound);
        assertEquals("Driver #123 not found", mappedNotFound.getMessage());

        StatusRuntimeException unauthEx =
                Status.UNAUTHENTICATED.withDescription("Missing JWT").asRuntimeException();
        BaseException mappedUnauth = GrpcStatusExceptionMapper.toBaseException(unauthEx);
        assertInstanceOf(UnauthorizedException.class, mappedUnauth);

        StatusRuntimeException invalidArgEx =
                Status.INVALID_ARGUMENT
                        .withDescription("Invalid GPS coordinate")
                        .asRuntimeException();
        BaseException mappedInvalid = GrpcStatusExceptionMapper.toBaseException(invalidArgEx);
        assertInstanceOf(BadRequestException.class, mappedInvalid);

        StatusRuntimeException timeoutEx =
                Status.DEADLINE_EXCEEDED
                        .withDescription("Service call deadline passed")
                        .asRuntimeException();
        BaseException mappedTimeout = GrpcStatusExceptionMapper.toBaseException(timeoutEx);
        assertInstanceOf(RequestTimeoutException.class, mappedTimeout);
    }

    @Test
    @DisplayName("Trace metadata keys should conform to W3C and ShareRide standards")
    void testGrpcMetadataHeaders() {
        assertNotNull(GrpcMetadataConstants.TRACE_ID_KEY);
        assertNotNull(GrpcMetadataConstants.SPAN_ID_KEY);
        assertNotNull(GrpcMetadataConstants.TRACEPARENT_KEY);
        assertNotNull(GrpcMetadataConstants.USER_ID_KEY);
        assertNotNull(GrpcMetadataConstants.TENANT_ID_KEY);
        assertNotNull(GrpcMetadataConstants.CORRELATION_ID_KEY);
    }
}

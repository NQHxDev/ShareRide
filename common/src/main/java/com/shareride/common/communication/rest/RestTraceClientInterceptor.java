package com.shareride.common.communication.rest;

import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.filter.TraceIdContext;
import com.shareride.common.filter.UserContextFilter;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * ClientHttpRequestInterceptor for RestTemplate / RestClient that propagates
 * distributed tracing, tenant context, and user authentication tokens into outgoing HTTP REST calls.
 */
public class RestTraceClientInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {

        String traceId = TraceIdContext.getTraceId();
        String spanId = TraceIdContext.getSpanId();
        boolean sampled = TraceIdContext.isSampled();

        if (traceId != null && !traceId.isBlank()) {
            request.getHeaders().set(TraceIdContext.TRACE_HEADER, traceId);
            request.getHeaders().set(TraceIdContext.REQUEST_ID_HEADER, traceId);
            request.getHeaders().set(TraceIdContext.TRACEPARENT_HEADER,
                    TraceIdContext.formatTraceparent(traceId, spanId, sampled));
        }
        if (spanId != null && !spanId.isBlank()) {
            request.getHeaders().set(TraceIdContext.SPAN_HEADER, spanId);
        }

        UserContext userContext = UserContextHolder.getContext();
        if (userContext != null) {
            if (userContext.getUserId() != null && !userContext.getUserId().isBlank()) {
                request.getHeaders().set(UserContextFilter.USER_ID_HEADER, userContext.getUserId());
            }
            if (userContext.getTenantId() != null && !userContext.getTenantId().isBlank()) {
                request.getHeaders().set(UserContextFilter.TENANT_ID_HEADER, userContext.getTenantId());
            }
            if (userContext.getCorrelationId() != null && !userContext.getCorrelationId().isBlank()) {
                request.getHeaders().set(UserContextFilter.CORRELATION_ID_HEADER, userContext.getCorrelationId());
            } else if (traceId != null) {
                request.getHeaders().set(UserContextFilter.CORRELATION_ID_HEADER, traceId);
            }
            if (userContext.getPriority() != null && !userContext.getPriority().isBlank()) {
                request.getHeaders().set(UserContextFilter.PRIORITY_HEADER, userContext.getPriority());
            }
            if (userContext.getFeatureFlags() != null && !userContext.getFeatureFlags().isEmpty()) {
                request.getHeaders().set(UserContextFilter.FEATURE_FLAGS_HEADER,
                        String.join(",", userContext.getFeatureFlags()));
            }
        }

        return execution.execute(request, body);
    }
}

package com.shareride.common;

import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.filter.TraceIdContext;
import com.shareride.common.filter.UserContextFilter;
import com.shareride.common.telemetry.BaggageUtils;
import com.shareride.common.telemetry.TracePropagationInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TracePropagationInterceptorTest {

   private TracePropagationInterceptor interceptor;

   @BeforeEach
   void setUp() {
      interceptor = new TracePropagationInterceptor();
      TraceIdContext.clear();
      UserContextHolder.clear();
   }

   @AfterEach
   void tearDown() {
      TraceIdContext.clear();
      UserContextHolder.clear();
   }

   @Test
   @DisplayName("Should inject X-Trace-Id, X-Request-Id, and W3C traceparent into outbound HTTP request")
   void testTracePropagation() throws IOException {
      String testTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
      String testSpanId = "00f067aa0ba902b7";

      TraceIdContext.setTraceId(testTraceId);
      TraceIdContext.setSpanId(testSpanId);
      TraceIdContext.setSampled(true);

      MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET,
            URI.create("http://ride-service/api/v1/rides"));

      ClientHttpRequestExecution execution = (req, body) -> {
         assertTrue(req.getHeaders().containsHeader(TraceIdContext.TRACE_HEADER));
         assertEquals(testTraceId, req.getHeaders().getFirst(TraceIdContext.TRACE_HEADER));

         assertTrue(req.getHeaders().containsHeader(TraceIdContext.REQUEST_ID_HEADER));
         assertEquals(testTraceId, req.getHeaders().getFirst(TraceIdContext.REQUEST_ID_HEADER));

         assertTrue(req.getHeaders().containsHeader(TraceIdContext.TRACEPARENT_HEADER));
         assertEquals("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
               req.getHeaders().getFirst(TraceIdContext.TRACEPARENT_HEADER));

         return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
      };

      ClientHttpResponse response = interceptor.intercept(request, new byte[0], execution);
      assertEquals(HttpStatus.OK, response.getStatusCode());
   }

   @Test
   @DisplayName("Should propagate business context baggage (tenantId, userId, priority, featureFlags, correlationId, W3C baggage)")
   void testBusinessBaggagePropagation() throws IOException {
      TraceIdContext.setTraceId("trace-1234");
      TraceIdContext.setSpanId("span-5678");

      UserContext userContext = UserContext.builder()
            .tenantId("tenant-vn")
            .userId("usr-42")
            .correlationId("corr-999")
            .priority("HIGH")
            .featureFlags(Set.of("surge_v2"))
            .build();
      UserContextHolder.setContext(userContext);

      MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.POST,
            URI.create("http://driver-service/api/v1/dispatch"));

      ClientHttpRequestExecution execution = (req, body) -> {
         assertEquals("tenant-vn", req.getHeaders().getFirst(UserContextFilter.TENANT_ID_HEADER));
         assertEquals("usr-42", req.getHeaders().getFirst(UserContextFilter.USER_ID_HEADER));
         assertEquals("corr-999", req.getHeaders().getFirst(UserContextFilter.CORRELATION_ID_HEADER));
         assertEquals("HIGH", req.getHeaders().getFirst(UserContextFilter.PRIORITY_HEADER));
         assertEquals("surge_v2", req.getHeaders().getFirst(UserContextFilter.FEATURE_FLAGS_HEADER));

         String baggage = req.getHeaders().getFirst(BaggageUtils.BAGGAGE_HEADER);
         assertNotNull(baggage);
         assertTrue(baggage.contains("tenantId=tenant-vn"));
         assertTrue(baggage.contains("userId=usr-42"));
         assertTrue(baggage.contains("priority=HIGH"));
         assertTrue(baggage.contains("correlationId=corr-999"));

         return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
      };

      ClientHttpResponse response = interceptor.intercept(request, new byte[0], execution);
      assertEquals(HttpStatus.OK, response.getStatusCode());
   }
}

package com.shareride.common.context;

import static org.junit.jupiter.api.Assertions.*;

import com.shareride.common.filter.TraceIdContext;
import com.shareride.common.logging.MdcConstants;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class ShareRideTaskDecoratorTest {

    private ShareRideTaskDecorator decorator;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        decorator = new ShareRideTaskDecorator();
        executorService = Executors.newSingleThreadExecutor();
        UserContextHolder.clear();
        TraceIdContext.clear();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
        TraceIdContext.clear();
        MDC.clear();
        executorService.shutdown();
    }

    @Test
    @DisplayName("Should propagate UserContext, TraceIdContext and MDC to async worker thread")
    void testPropagateContextToAsyncThread() throws Exception {
        // Setup caller thread state
        UserContext callerUser =
                UserContext.builder()
                        .userId("async-usr-1")
                        .email("async@shareride.com")
                        .roles(Set.of("PASSENGER"))
                        .clientIp("10.0.0.1")
                        .deviceId("device-async-1")
                        .traceId("trace-decorated-123")
                        .spanId("span-decorated-456")
                        .build();
        UserContextHolder.setContext(callerUser);

        TraceIdContext.setTraceId("trace-decorated-123");
        TraceIdContext.setSpanId("span-decorated-456");

        MDC.put(MdcConstants.TRACE_ID, "trace-decorated-123");
        MDC.put(MdcConstants.USER_ID, "async-usr-1");
        MDC.put("customKey", "customVal");

        AtomicReference<String> workerUserId = new AtomicReference<>();
        AtomicReference<String> workerTraceId = new AtomicReference<>();
        AtomicReference<String> workerSpanId = new AtomicReference<>();
        AtomicReference<String> workerMdcTrace = new AtomicReference<>();
        AtomicReference<String> workerMdcCustom = new AtomicReference<>();

        Runnable task =
                () -> {
                    workerUserId.set(UserContextHolder.getCurrentUserId().orElse(null));
                    workerTraceId.set(TraceIdContext.getTraceId());
                    workerSpanId.set(TraceIdContext.getSpanId());
                    workerMdcTrace.set(MDC.get(MdcConstants.TRACE_ID));
                    workerMdcCustom.set(MDC.get("customKey"));
                };

        Runnable decorated = decorator.decorate(task);
        Future<?> future = executorService.submit(decorated);
        future.get(2, TimeUnit.SECONDS);

        assertEquals("async-usr-1", workerUserId.get());
        assertEquals("trace-decorated-123", workerTraceId.get());
        assertEquals("span-decorated-456", workerSpanId.get());
        assertEquals("trace-decorated-123", workerMdcTrace.get());
        assertEquals("customVal", workerMdcCustom.get());

        // Ensure caller thread state is intact
        assertEquals("async-usr-1", UserContextHolder.getRequiredUserId());
        assertEquals("trace-decorated-123", TraceIdContext.getTraceId());
        assertEquals("customVal", MDC.get("customKey"));
    }

    @Test
    @DisplayName("Worker thread should restore previous state after task completion")
    void testWorkerThreadRestoresPreviousState() throws Exception {
        // Setup pre-existing state on worker thread
        executorService
                .submit(
                        () -> {
                            MDC.put("preExistingKey", "preExistingValue");
                            TraceIdContext.setTraceId("pre-existing-trace");
                        })
                .get(2, TimeUnit.SECONDS);

        // Caller has clean context
        Runnable task =
                () -> {
                    assertNull(MDC.get("preExistingKey"));
                };

        Runnable decorated = decorator.decorate(task);
        executorService.submit(decorated).get(2, TimeUnit.SECONDS);

        // Verify worker thread restored its pre-existing values
        AtomicReference<String> restoredKey = new AtomicReference<>();
        AtomicReference<String> restoredTrace = new AtomicReference<>();
        executorService
                .submit(
                        () -> {
                            restoredKey.set(MDC.get("preExistingKey"));
                            restoredTrace.set(TraceIdContext.getTraceId());
                        })
                .get(2, TimeUnit.SECONDS);

        assertEquals("preExistingValue", restoredKey.get());
        assertEquals("pre-existing-trace", restoredTrace.get());
    }
}

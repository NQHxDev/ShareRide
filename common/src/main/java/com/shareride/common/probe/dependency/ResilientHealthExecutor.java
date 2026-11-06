package com.shareride.common.probe.dependency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;

import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Resilient executor that isolates health check probes with a strict timeout
 * and circuit breaker to prevent slow dependencies from exhausting Actuator
 * worker threads.
 */
public class ResilientHealthExecutor {

   private static final Logger log = LoggerFactory.getLogger(ResilientHealthExecutor.class);

   private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
      Thread thread = new Thread(r, "health-check-worker");
      thread.setDaemon(true);
      return thread;
   });

   private final SimpleHealthCircuitBreaker circuitBreaker;
   private final long timeoutMs;

   public ResilientHealthExecutor(String dependencyName) {
      this(dependencyName, 1500L);
   }

   public ResilientHealthExecutor(String dependencyName, long timeoutMs) {
      this(new SimpleHealthCircuitBreaker(dependencyName), timeoutMs);
   }

   public ResilientHealthExecutor(SimpleHealthCircuitBreaker circuitBreaker, long timeoutMs) {
      this.circuitBreaker = circuitBreaker;
      this.timeoutMs = timeoutMs;
   }

   public ResilientHealthExecutor(String dependencyName, long timeoutMs, SimpleHealthCircuitBreaker circuitBreaker) {
      this(circuitBreaker != null ? circuitBreaker : new SimpleHealthCircuitBreaker(dependencyName), timeoutMs);
   }

   public SimpleHealthCircuitBreaker getCircuitBreaker() {
      return circuitBreaker;
   }

   /**
    * Executes the given health check supplier within timeout and under circuit
    * breaker.
    */
   public Health execute(Supplier<Health> healthSupplier) {
      if (!circuitBreaker.allowExecution()) {
         log.warn("Health check for [{}] skipped: Circuit breaker is OPEN", circuitBreaker.getName());
         return Health.down()
               .withDetail("circuitBreaker", CircuitBreakerState.OPEN.name())
               .withDetail("status", "CIRCUIT_BREAKER_OPEN")
               .withDetail("error",
                     "Circuit open due to consecutive failures: " + circuitBreaker.getConsecutiveFailures())
               .build();
      }

      long start = System.currentTimeMillis();
      CompletableFuture<Health> future = CompletableFuture.supplyAsync(healthSupplier, EXECUTOR);

      try {
         Health result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
         long latency = System.currentTimeMillis() - start;

         if (result.getStatus().equals(org.springframework.boot.health.contributor.Status.UP)) {
            circuitBreaker.recordSuccess();
            return Health.status(result.getStatus())
                  .withDetails(result.getDetails())
                  .withDetail("latencyMs", latency)
                  .withDetail("circuitBreaker", circuitBreaker.getState().name())
                  .build();
         } else {
            circuitBreaker.recordFailure();
            return Health.status(result.getStatus())
                  .withDetails(result.getDetails())
                  .withDetail("latencyMs", latency)
                  .withDetail("circuitBreaker", circuitBreaker.getState().name())
                  .build();
         }
      } catch (TimeoutException e) {
         future.cancel(true);
         circuitBreaker.recordFailure();
         log.warn("Health check for [{}] timed out after {}ms", circuitBreaker.getName(), timeoutMs);
         return Health.down()
               .withDetail("circuitBreaker", circuitBreaker.getState().name())
               .withDetail("status", "TIMEOUT")
               .withDetail("error", "Health check timed out after " + timeoutMs + "ms")
               .build();
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         circuitBreaker.recordFailure();
         return Health.down()
               .withDetail("circuitBreaker", circuitBreaker.getState().name())
               .withDetail("status", "INTERRUPTED")
               .withDetail("error", "Health check thread interrupted")
               .build();
      } catch (ExecutionException e) {
         circuitBreaker.recordFailure();
         Throwable cause = e.getCause() != null ? e.getCause() : e;
         log.warn("Health check for [{}] failed: {}", circuitBreaker.getName(), cause.getMessage());
         return Health.down(cause)
               .withDetail("circuitBreaker", circuitBreaker.getState().name())
               .withDetail("status", "FAILED")
               .withDetail("error", cause.getMessage())
               .build();
      }
   }
}

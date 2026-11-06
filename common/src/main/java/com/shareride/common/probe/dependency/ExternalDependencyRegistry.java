package com.shareride.common.probe.dependency;

import org.springframework.boot.health.contributor.Health;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Registry where microservices can register external third-party API
 * dependencies
 * (Payment Gateways, Google Maps, SMS providers) for automated health probing.
 */
public class ExternalDependencyRegistry {

   public record DependencyCheck(String name, Supplier<Health> checkSupplier, boolean critical,
         ResilientHealthExecutor executor) {
   }

   private final Map<String, DependencyCheck> dependencies = new ConcurrentHashMap<>();

   /**
    * Register a critical external dependency with default 2000ms timeout and
    * circuit breaker.
    */
   public void register(String name, Supplier<Health> checkSupplier) {
      register(name, checkSupplier, true, 2000L);
   }

   /**
    * Register an external dependency indicating whether it is critical (pod marked
    * DOWN on failure)
    * or non-critical (pod remains UP but marked DEGRADED).
    */
   public void register(String name, Supplier<Health> checkSupplier, boolean critical) {
      register(name, checkSupplier, critical, 2000L);
   }

   /**
    * Register an external dependency with specific timeout and circuit breaker.
    */
   public void register(String name, Supplier<Health> checkSupplier, boolean critical, long timeoutMs) {
      ResilientHealthExecutor executor = new ResilientHealthExecutor("ext-" + name, timeoutMs);
      dependencies.put(name, new DependencyCheck(name, checkSupplier, critical, executor));
   }

   public void unregister(String name) {
      dependencies.remove(name);
   }

   public Map<String, DependencyCheck> getDependencies() {
      return dependencies;
   }

   public boolean isEmpty() {
      return dependencies.isEmpty();
   }
}

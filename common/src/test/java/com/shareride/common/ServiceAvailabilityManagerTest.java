package com.shareride.common;

import com.shareride.common.probe.ServiceAvailabilityManager;
import com.shareride.common.probe.ShareRideAppHealthIndicator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.ApplicationAvailabilityBean;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.*;

class ServiceAvailabilityManagerTest {

   private ApplicationAvailabilityBean availabilityBean;
   private ServiceAvailabilityManager availabilityManager;
   private ShareRideAppHealthIndicator healthIndicator;

   @BeforeEach
   void setUp() {
      availabilityBean = new ApplicationAvailabilityBean();
      ApplicationEventPublisher publisher = event -> {
         if (event instanceof AvailabilityChangeEvent<?> ace) {
            availabilityBean.onApplicationEvent(ace);
         }
      };

      availabilityManager = new ServiceAvailabilityManager(availabilityBean, publisher);
      healthIndicator = new ShareRideAppHealthIndicator(availabilityManager, "ride-service");
   }

   @Test
   @DisplayName("Should initialize and transition readiness states correctly")
   void testReadinessProbeTransitions() {
      // Initially null in unit test bean until published
      availabilityManager.markReady();
      assertTrue(availabilityManager.isReady());
      assertEquals(ReadinessState.ACCEPTING_TRAFFIC, availabilityManager.getReadinessState());

      // Graceful drain or cache refresh
      availabilityManager.markNotReady("Warmup cache in progress");
      assertFalse(availabilityManager.isReady());
      assertEquals(ReadinessState.REFUSING_TRAFFIC, availabilityManager.getReadinessState());

      // Back to accepting
      availabilityManager.markReady();
      assertTrue(availabilityManager.isReady());
   }

   @Test
   @DisplayName("Should initialize and transition liveness states correctly")
   void testLivenessProbeTransitions() {
      availabilityManager.markLive();
      assertTrue(availabilityManager.isLive());
      assertEquals(LivenessState.CORRECT, availabilityManager.getLivenessState());

      availabilityManager.markBroken("Deadlock detected in worker thread pool");
      assertFalse(availabilityManager.isLive());
      assertEquals(LivenessState.BROKEN, availabilityManager.getLivenessState());
   }

   @Test
   @DisplayName("ShareRideAppHealthIndicator should expose detailed health info")
   void testHealthIndicator() {
      availabilityManager.markLive();
      availabilityManager.markReady();

      Health health = healthIndicator.health();
      assertEquals(Status.UP, health.getStatus());
      assertEquals("ride-service", health.getDetails().get("application"));
      assertEquals("CORRECT", health.getDetails().get("liveness"));
      assertEquals("ACCEPTING_TRAFFIC", health.getDetails().get("readiness"));
      assertEquals("OPERATIONAL", health.getDetails().get("state"));
      assertNotNull(health.getDetails().get("uptimeSeconds"));

      // If liveness broken, status must be DOWN
      availabilityManager.markBroken("Fatal memory error");
      Health downHealth = healthIndicator.health();
      assertEquals(Status.DOWN, downHealth.getStatus());
      assertEquals("DEADLOCK_OR_BROKEN", downHealth.getDetails().get("state"));
   }
}

package com.shareride.common.logging.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shareride.common.context.CorrelationIdContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.filter.TraceIdContext;
import com.shareride.common.logging.masking.DataMaskingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * Standardized logger for publishing business domain lifecycle events.
 */
public class BusinessEventLogger {

   private static final Logger log = LoggerFactory.getLogger("com.shareride.business.event");
   private static final Marker EVENT_MARKER = MarkerFactory.getMarker("BUSINESS_EVENT");
   private static final ObjectMapper objectMapper = new ObjectMapper();

   public void publish(BusinessEvent event) {
      if (event == null) {
         return;
      }
      if (event.getTraceId() == null) {
         event.setTraceId(TraceIdContext.getTraceId());
      }
      if (event.getCorrelationId() == null) {
         String correlationId = CorrelationIdContext.getCorrelationId();
         if (correlationId == null || correlationId.isBlank()) {
            correlationId = UserContextHolder.getCorrelationId().orElse(null);
         }
         event.setCorrelationId(correlationId);
      }
      if (event.getMetadata() != null) {
         event.setMetadata(DataMaskingUtils.maskParams(event.getMetadata()));
      }

      try {
         String json = objectMapper.writeValueAsString(event);
         log.info(EVENT_MARKER, "[BUSINESS_EVENT] {}", json);
      } catch (Exception e) {
         log.info(EVENT_MARKER, "[BUSINESS_EVENT] type={} user={} entity={} traceId={} correlationId={}",
               event.getType(), event.getUserId(), event.getEntityId(), event.getTraceId(), event.getCorrelationId());
      }
   }
}

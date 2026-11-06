package com.shareride.common.telemetry;

import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.filter.TraceIdContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseQueryContextTest {

   @AfterEach
   void tearDown() {
      TraceIdContext.clear();
      UserContextHolder.clear();
   }

   @Test
   void testFormatSqlCommentWithoutUser() {
      TraceIdContext.setTraceId("sql-trace-1");
      TraceIdContext.setSpanId("sql-span-2");

      String comment = DatabaseQueryContext.formatSqlComment();
      assertTrue(comment.startsWith("/* traceId=sql-trace-1, spanId=sql-span-2 */"));
   }

   @Test
   void testFormatSqlCommentWithUserAndTenant() {
      TraceIdContext.setTraceId("sql-trace-1");
      TraceIdContext.setSpanId("sql-span-2");

      UserContext user = UserContext.builder()
            .userId("u-driver-88")
            .tenantId("tenant-sg")
            .build();
      UserContextHolder.setContext(user);

      String comment = DatabaseQueryContext.formatSqlComment();
      assertTrue(comment.contains("userId=u-driver-88"));
      assertTrue(comment.contains("tenantId=tenant-sg"));
   }

   @Test
   void testAttachComment() {
      TraceIdContext.setTraceId("trace-xyz");
      TraceIdContext.setSpanId("span-abc");

      String sql = "SELECT * FROM rides WHERE id = 1";
      String enrichedSql = DatabaseQueryContext.attachComment(sql);

      assertTrue(enrichedSql.startsWith("/* traceId=trace-xyz, spanId=span-abc */ SELECT * FROM rides WHERE id = 1"));
      assertNull(DatabaseQueryContext.attachComment(null));
      assertEquals("   ", DatabaseQueryContext.attachComment("   "));
   }
}

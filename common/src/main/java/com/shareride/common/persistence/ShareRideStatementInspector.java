package com.shareride.common.persistence;

import com.shareride.common.telemetry.DatabaseQueryContext;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Hibernate StatementInspector automatically injecting distributed trace
 * identifiers
 * and user context comments into every SQL statement executed against the
 * relational database.
 */
public class ShareRideStatementInspector implements StatementInspector {

   @Override
   public String inspect(String sql) {
      return DatabaseQueryContext.inspect(sql);
   }

}

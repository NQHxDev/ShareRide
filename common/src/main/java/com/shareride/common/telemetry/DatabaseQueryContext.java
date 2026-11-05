package com.shareride.common.telemetry;

import com.shareride.common.context.UserContext;
import com.shareride.common.context.UserContextHolder;
import com.shareride.common.filter.TraceIdContext;

/**
 * Context generator for SQL comment correlation in Database queries and Hibernate logs.
 */
public final class DatabaseQueryContext {

    private DatabaseQueryContext() {
    }

    /**
     * Generates a SQL comment containing distributed tracing and user correlation identifiers.
     * Example: "/* traceId=abc, spanId=def, userId=u123, tenantId=t456 *&#47; "
     */
    public static String formatSqlComment() {
        String traceId = TraceIdContext.getTraceId();
        String spanId = TraceIdContext.getSpanId();
        UserContext user = UserContextHolder.getContext();

        StringBuilder sb = new StringBuilder("/* ");
        sb.append("traceId=").append(traceId != null ? traceId : "none");
        sb.append(", spanId=").append(spanId != null ? spanId : "none");

        if (user != null) {
            if (user.getUserId() != null && !user.getUserId().isBlank()) {
                sb.append(", userId=").append(user.getUserId());
            }
            if (user.getTenantId() != null && !user.getTenantId().isBlank()) {
                sb.append(", tenantId=").append(user.getTenantId());
            }
        }
        sb.append(" */ ");
        return sb.toString();
    }

    /**
     * Prepends SQL comment correlation to a query string.
     */
    public static String attachComment(String sql) {
        if (sql == null || sql.isBlank()) {
            return sql;
        }
        return formatSqlComment() + sql.trim();
    }

    /**
     * Inspects SQL statement and attaches correlation comment (Hibernate StatementInspector compliant).
     */
    public static String inspect(String sql) {
        return attachComment(sql);
    }
}

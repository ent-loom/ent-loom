package com.entloom.ddl.core;

import com.entloom.ddl.api.SqlExecutor;
import java.util.List;

/**
 * 默认执行器：只生成 SQL，不落库执行。
 */
public final class NoopSqlExecutor implements SqlExecutor {
    @Override
    public void execute(List<String> sqlStatements) {
        // no-op
    }
}

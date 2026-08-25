package com.entloom.ddl.core;

import com.entloom.ddl.api.SqlExecutor;
import java.util.List;

/**
 * 显式 dry-run 执行器：接收 SQL 但不落库执行。
 *
 * <p>该实现只用于测试或预览，不代表生产执行成功。</p>
 */
public final class NoopSqlExecutor implements SqlExecutor {
    @Override
    public boolean isDryRun() {
        return true;
    }

    @Override
    public void execute(List<String> sqlStatements) {
        // no-op
    }
}

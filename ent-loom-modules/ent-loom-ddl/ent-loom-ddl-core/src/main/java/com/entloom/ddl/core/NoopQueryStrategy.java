package com.entloom.ddl.core;

import com.entloom.ddl.api.QueryStrategy;

/**
 * 默认查询策略：始终视为表不存在。
 */
public final class NoopQueryStrategy implements QueryStrategy {
    @Override
    public boolean tableExists(String schema, String tableName) {
        return false;
    }
}

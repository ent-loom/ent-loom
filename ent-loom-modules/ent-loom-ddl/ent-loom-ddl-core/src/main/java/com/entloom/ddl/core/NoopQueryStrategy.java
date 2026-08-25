package com.entloom.ddl.core;

import com.entloom.ddl.api.QueryStrategy;

/**
 * 显式 dry-run 查询策略：始终视为表不存在。
 *
 * <p>该实现只用于测试或预览，不代表生产数据库状态。</p>
 */
public final class NoopQueryStrategy implements QueryStrategy {
    @Override
    public boolean tableExists(String schema, String tableName) {
        return false;
    }
}

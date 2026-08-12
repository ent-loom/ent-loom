package com.entloom.ddl.api;

/**
 * 数据库查询策略，通常由方言或数据源适配层实现。
 */
public interface QueryStrategy {
    /**
     * 判断目标表是否已存在。
     */
    boolean tableExists(String schema, String tableName);
}

package com.entloom.ddl.api;

/**
 * 数据库查询策略，通常由方言或数据源适配层实现。
 */
public interface QueryStrategy {
    /**
     * 判断目标表是否已存在。
     */
    boolean tableExists(String schema, String tableName);

    /**
     * 读取当前表结构，供 E3 差异计算使用。
     *
     * <p>旧的只支持建表的查询策略无需实现此方法；调用修改模式时会明确失败，
     * 不会把无法读取的现状当成空表。</p>
     */
    default DdlTableSnapshot readTable(String schema, String tableName) {
        throw new UnsupportedOperationException("QueryStrategy 未提供表结构读取能力");
    }
}

package com.entloom.ddl.api;

/**
 * 数据库查询策略，通常由方言或数据源适配层实现。
 */
public interface QueryStrategy {
    /** 读取目标库默认字符集；schema 为空时使用当前连接的库，不使用连接字符集。 */
    default DdlCharacterSetMetadata readDatabaseCharacterSet(String schema) {
        throw new UnsupportedOperationException("QueryStrategy 未提供目标库字符集读取能力");
    }

    /** 读取新建库将继承的服务器默认字符集，不代替已有库、表、列的实际配置。 */
    default DdlCharacterSetMetadata readServerCharacterSet() {
        throw new UnsupportedOperationException("QueryStrategy 未提供服务器默认字符集读取能力");
    }

    /**
     * 判断目标表是否已存在。
     */
    boolean tableExists(String schema, String tableName);

    /**
     * 读取当前表结构，供受控修改和全量同步差异计算使用。
     *
     * <p>旧的只支持建表的查询策略无需实现此方法；调用修改模式时会明确失败，
     * 不会把无法读取的现状当成空表。</p>
     */
    default DdlTableSnapshot readTable(String schema, String tableName) {
        throw new UnsupportedOperationException("QueryStrategy 未提供表结构读取能力");
    }
}

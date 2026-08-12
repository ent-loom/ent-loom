package com.entloom.crud.core.capability.command.aggregate;

/**
 * 聚合子集合写入模式。
 */
public enum ChildSyncMode {
    /**
     * 以请求中的子集合为准，替换当前父记录下的子集合。
     */
    REPLACE
}

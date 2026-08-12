package com.entloom.crud.core.capability.query;

/**
 * 查询编译器。
 */
public interface QueryCompiler {
    /**
     * 将查询计划编译为可执行 SQL。
     *
     * @param plan 查询计划
     * @return 编译结果
     */
    CompiledQuery compile(QueryPlan plan);
}

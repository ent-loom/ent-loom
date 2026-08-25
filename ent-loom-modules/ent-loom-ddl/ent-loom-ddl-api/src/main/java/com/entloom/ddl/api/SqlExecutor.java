package com.entloom.ddl.api;

import java.util.List;
import java.util.Collections;

/**
 * SQL 执行器。
 */
public interface SqlExecutor {
    void execute(List<String> sqlStatements);

    /**
     * 执行一条 SQL。默认实现保持旧的批量执行器兼容，并为引擎提供逐条进度边界。
     */
    default void executeOne(String sqlStatement) {
        execute(Collections.singletonList(sqlStatement));
    }

    /**
     * 判断当前执行器是否只做 SQL 预览而不落库。
     *
     * <p>真实执行器保持默认值 {@code false}；显式 dry-run 执行器应返回
     * {@code true}，引擎不会将生成的 SQL 伪装为已执行 SQL。</p>
     */
    default boolean isDryRun() {
        return false;
    }
}

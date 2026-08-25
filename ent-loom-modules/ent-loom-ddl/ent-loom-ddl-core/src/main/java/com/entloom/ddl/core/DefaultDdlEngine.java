package com.entloom.ddl.core;

import com.entloom.ddl.api.DdlEngine;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlExecutionMode;
import com.entloom.ddl.api.DdlExecutionRequest;
import com.entloom.ddl.api.DdlExecutionResult;
import com.entloom.ddl.api.QueryStrategy;
import com.entloom.ddl.api.SqlExecutor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 默认 DDL 引擎（E1 只编排建表路径）。
 */
public final class DefaultDdlEngine implements DdlEngine {
    private final MysqlCreateTableSqlBuilder createTableSqlBuilder;

    public DefaultDdlEngine() {
        this(new MysqlCreateTableSqlBuilder());
    }

    public DefaultDdlEngine(MysqlCreateTableSqlBuilder createTableSqlBuilder) {
        this.createTableSqlBuilder = createTableSqlBuilder == null ? new MysqlCreateTableSqlBuilder() : createTableSqlBuilder;
    }

    @Override
    public DdlExecutionResult execute(DdlExecutionRequest request, QueryStrategy queryStrategy, SqlExecutor sqlExecutor) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.mode() == DdlExecutionMode.NONE) {
            return new DdlExecutionResult(Collections.<String>emptyList(),
                    Collections.<String>emptyList(),
                    Collections.<String>emptyList());
        }
        List<String> generatedSql = new ArrayList<String>();
        List<String> executedSql = new ArrayList<String>();
        List<String> errors = new ArrayList<String>();

        if (request.mode() != DdlExecutionMode.CREATE_TABLE
                && request.mode() != DdlExecutionMode.CREATE_TABLE_AND_METAS) {
            errors.add("E1 不支持执行模式: " + request.mode());
            return new DdlExecutionResult(generatedSql, executedSql, errors);
        }

        String globalSchema = trim(request.schema());
        if (request.createDatabaseIfMissing() && !globalSchema.isEmpty()) {
            generatedSql.add("CREATE DATABASE IF NOT EXISTS " + quote(globalSchema));
        }

        for (DdlEntityMetadata entity : request.entities()) {
            String schema = resolveSchema(entity, globalSchema);
            try {
                boolean tableExists = queryStrategy != null && queryStrategy.tableExists(schema, entity.tableName());
                if (!tableExists) {
                    generatedSql.add(createTableSqlBuilder.build(entity, schema));
                }
            } catch (RuntimeException ex) {
                errors.add(formatError(entity, ex));
            }
        }

        // 生成阶段有错误时不执行不完整的计划，避免把部分结果误报为完整执行。
        if (errors.isEmpty() && !generatedSql.isEmpty() && sqlExecutor != null) {
            try {
                sqlExecutor.execute(generatedSql);
                if (!sqlExecutor.isDryRun()) {
                    executedSql.addAll(generatedSql);
                }
            } catch (RuntimeException ex) {
                errors.add(formatError("SQL 执行失败", ex));
            }
        }
        return new DdlExecutionResult(generatedSql, executedSql, errors);
    }

    private static String resolveSchema(DdlEntityMetadata entity, String fallback) {
        String entitySchema = trim(entity.schema());
        return entitySchema.isEmpty() ? fallback : entitySchema;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String formatError(DdlEntityMetadata entity, RuntimeException ex) {
        return formatError("表 " + entity.tableName() + " 处理失败", ex);
    }

    private static String formatError(String prefix, RuntimeException ex) {
        String detail = ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage();
        return prefix + ": " + detail;
    }

    private static String quote(String value) {
        return "`" + value.replace("`", "``") + "`";
    }
}

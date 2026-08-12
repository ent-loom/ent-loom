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
 * 默认 DDL 引擎（当前阶段先支持 create path）。
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

        String globalSchema = trim(request.schema());
        if (request.createDatabaseIfMissing() && !globalSchema.isEmpty()) {
            generatedSql.add("CREATE DATABASE IF NOT EXISTS `" + globalSchema + "`");
        }

        for (DdlEntityMetadata entity : request.entities()) {
            if (entity == null) {
                continue;
            }
            String schema = resolveSchema(entity, globalSchema);
            boolean tableExists = queryStrategy != null && queryStrategy.tableExists(schema, entity.tableName());
            if (!tableExists) {
                generatedSql.add(createTableSqlBuilder.build(entity, schema));
            }
        }

        if (!generatedSql.isEmpty() && sqlExecutor != null) {
            try {
                sqlExecutor.execute(generatedSql);
                executedSql.addAll(generatedSql);
            } catch (RuntimeException ex) {
                errors.add(ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage());
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
}

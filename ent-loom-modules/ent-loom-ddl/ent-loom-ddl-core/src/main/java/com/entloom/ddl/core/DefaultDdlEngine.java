package com.entloom.ddl.core;

import com.entloom.ddl.api.DdlEngine;
import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlExecutionMode;
import com.entloom.ddl.api.DdlExecutionRequest;
import com.entloom.ddl.api.DdlExecutionResult;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.DdlIndexMetadata;
import com.entloom.ddl.api.DdlTableSnapshot;
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
    private final MysqlAlterTableSqlBuilder alterTableSqlBuilder;
    private final DdlSchemaDiffer schemaDiffer;

    public DefaultDdlEngine() {
        this(new MysqlCreateTableSqlBuilder());
    }

    public DefaultDdlEngine(MysqlCreateTableSqlBuilder createTableSqlBuilder) {
        this.createTableSqlBuilder = createTableSqlBuilder == null ? new MysqlCreateTableSqlBuilder() : createTableSqlBuilder;
        this.alterTableSqlBuilder = new MysqlAlterTableSqlBuilder(this.createTableSqlBuilder);
        this.schemaDiffer = new DdlSchemaDiffer();
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
                && request.mode() != DdlExecutionMode.CREATE_TABLE_AND_METAS
                && request.mode() != DdlExecutionMode.CREATE_MODIFY_TABLE_AND_METAS) {
            errors.add("E3 不支持执行模式: " + request.mode());
            return new DdlExecutionResult(generatedSql, executedSql, errors);
        }

        boolean modifyMode = request.mode() == DdlExecutionMode.CREATE_MODIFY_TABLE_AND_METAS;
        if (modifyMode && queryStrategy == null) {
            errors.add("E3 修改模式需要 QueryStrategy.readTable 提供当前表结构");
            return new DdlExecutionResult(generatedSql, executedSql, errors);
        }

        String globalSchema = trim(request.schema());
        if (request.createDatabaseIfMissing() && !globalSchema.isEmpty()) {
            generatedSql.add("CREATE DATABASE IF NOT EXISTS " + quote(globalSchema));
        }

        for (DdlEntityMetadata entity : request.entities()) {
            String schema = resolveSchema(entity, globalSchema);
            try {
                if (modifyMode) {
                    DdlTableSnapshot current = queryStrategy.readTable(schema, entity.tableName());
                    if (current == null) {
                        throw new IllegalStateException("QueryStrategy.readTable 返回了 null");
                    }
                    if (!current.exists()) {
                        generatedSql.add(createTableSqlBuilder.build(entity, schema));
                    } else {
                        appendModifySql(entity, schema, current, generatedSql, errors);
                    }
                } else {
                    boolean tableExists = queryStrategy != null && queryStrategy.tableExists(schema, entity.tableName());
                    if (!tableExists) {
                        generatedSql.add(createTableSqlBuilder.build(entity, schema));
                    }
                }
            } catch (RuntimeException ex) {
                errors.add(formatError(entity, ex));
            }
        }

        // 生成阶段有错误时不执行不完整的计划，避免把部分结果误报为完整执行。
        if (errors.isEmpty() && !generatedSql.isEmpty() && sqlExecutor != null) {
            for (String sql : generatedSql) {
                try {
                    sqlExecutor.executeOne(sql);
                    if (!sqlExecutor.isDryRun()) {
                        executedSql.add(sql);
                    }
                } catch (RuntimeException ex) {
                    String prefix = executedSql.isEmpty()
                            ? "SQL 执行失败"
                            : "SQL 执行失败，已确认执行 " + executedSql.size() + " 条";
                    errors.add(formatError(prefix, ex));
                    break;
                }
            }
        }
        return new DdlExecutionResult(generatedSql, executedSql, errors);
    }

    private void appendModifySql(DdlEntityMetadata entity,
                                 String schema,
                                 DdlTableSnapshot current,
                                 List<String> generatedSql,
                                 List<String> errors) {
        DdlSchemaDiff diff = schemaDiffer.diff(entity, current);
        if (!diff.errors().isEmpty()) {
            errors.add("表 " + entity.tableName() + " 差异校验失败: " + diff.errors());
            return;
        }
        if (diff.tableCommentChanged()) {
            generatedSql.add(alterTableSqlBuilder.buildComment(entity, schema));
        }
        for (DdlFieldMetadata field : diff.addedFields()) {
            generatedSql.add(alterTableSqlBuilder.buildAddColumn(entity, schema, field));
        }
        for (DdlFieldChange change : diff.changedFields()) {
            generatedSql.add(change.renamed()
                    ? alterTableSqlBuilder.buildChangeColumn(entity, schema, change)
                    : alterTableSqlBuilder.buildModifyColumn(entity, schema, change));
        }
        for (DdlIndexMetadata index : diff.addedIndexes()) {
            generatedSql.add(alterTableSqlBuilder.buildAddIndex(entity, schema, index));
        }
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

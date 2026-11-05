package com.entloom.ddl.core;

import com.entloom.ddl.api.DdlEngine;
import com.entloom.ddl.api.DdlCharacterSetMetadata;
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
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 默认 DDL 引擎，编排建表、受控修改和显式全量同步。
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
        List<String> warnings = new ArrayList<String>();

        if (request.mode() != DdlExecutionMode.CREATE_TABLE
                && request.mode() != DdlExecutionMode.CREATE_TABLE_AND_METAS
                && request.mode() != DdlExecutionMode.CREATE_MODIFY_TABLE_AND_METAS
                && request.mode() != DdlExecutionMode.CREATE_MODIFY_DELETE_ALL) {
            errors.add("不支持执行模式: " + request.mode());
            return new DdlExecutionResult(generatedSql, executedSql, errors);
        }

        boolean synchronizeAll = request.mode() == DdlExecutionMode.CREATE_MODIFY_DELETE_ALL;
        boolean modifyMode = request.mode() == DdlExecutionMode.CREATE_MODIFY_TABLE_AND_METAS || synchronizeAll;
        if (modifyMode && queryStrategy == null) {
            errors.add("修改模式需要 QueryStrategy.readTable 提供当前表结构");
            return new DdlExecutionResult(generatedSql, executedSql, errors);
        }

        String globalSchema = trim(request.schema());
        Map<String, DdlCharacterSetMetadata> databaseCharacterSets = new LinkedHashMap<String, DdlCharacterSetMetadata>();
        boolean createDatabase = request.createDatabaseIfMissing() && !globalSchema.isEmpty();
        // 按需读取数据库事实；所有实体校验结束后才允许执行建库与结构修改。
        for (DdlEntityMetadata entity : request.entities()) {
            String schema = resolveSchema(entity, globalSchema);
            try {
                boolean needsContext = createTableSqlBuilder.needsCharacterSet(entity);
                DdlTableSnapshot current = null;
                if (modifyMode || needsContext) {
                    if (queryStrategy == null && needsContext) {
                        throw new IllegalStateException("文本类型解析需要 QueryStrategy 提供有效字符集上下文");
                    }
                    current = queryStrategy.readTable(schema, entity.tableName());
                    if (current == null) { throw new IllegalStateException("QueryStrategy.readTable 返回了 null"); }
                }
                boolean exists = current != null ? current.exists()
                        : queryStrategy != null && queryStrategy.tableExists(schema, entity.tableName());
                DdlCharacterSetMetadata characterSet = current != null && current.exists()
                        ? current.characterSet() : DdlCharacterSetMetadata.unknown();
                if (!exists && needsContext) {
                    characterSet = databaseCharacterSets.get(schema);
                    if (characterSet == null) {
                        characterSet = queryStrategy.readDatabaseCharacterSet(schema);
                        if (!characterSet.known() && createDatabase && schema.equals(globalSchema)) {
                            characterSet = queryStrategy.readServerCharacterSet();
                        }
                        if (!characterSet.known()) { throw new IllegalStateException("无法确定目标库字符集: " + schema); }
                        databaseCharacterSets.put(schema, characterSet);
                    }
                }
                MysqlTableResolution resolution = createTableSqlBuilder.resolve(entity, current, characterSet);
                // 已有表也验证全部目标模型，不能通过 CREATE IF NOT EXISTS 跳过错误声明。
                String createSql = createTableSqlBuilder.build(entity, schema, resolution);
                if (!exists) {
                    generatedSql.add(createSql);
                    warnings.addAll(resolution.warnings());
                } else if (modifyMode) {
                    appendModifySql(entity, schema, current, generatedSql, errors, warnings, synchronizeAll, resolution);
                } else {
                    warnings.addAll(resolution.warnings());
                }
            } catch (RuntimeException ex) {
                errors.add(formatError(entity, ex));
            }
        }
        if (createDatabase) {
            DdlCharacterSetMetadata database = databaseCharacterSets.get(globalSchema);
            generatedSql.add(0, "CREATE DATABASE IF NOT EXISTS " + quote(globalSchema)
                    + (database != null && database.known() ? " DEFAULT CHARACTER SET " + database.characterSet()
                        + " COLLATE " + database.collation() : ""));
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
        return new DdlExecutionResult(generatedSql, executedSql, errors, warnings);
    }

    private void appendModifySql(DdlEntityMetadata entity,
                                 String schema,
                                 DdlTableSnapshot current,
                                 List<String> generatedSql,
                                 List<String> errors,
                                 List<String> warnings,
                                 boolean synchronizeAll, MysqlTableResolution resolution) {
        DdlSchemaDiff diff = schemaDiffer.diff(entity, current, synchronizeAll, resolution);
        for (String warning : diff.warnings()) {
            warnings.add("表 " + entity.tableName() + ": " + warning);
        }
        if (!diff.errors().isEmpty()) {
            for (String error : diff.errors()) {
                errors.add("表 " + entity.tableName() + " 差异校验失败: " + error);
            }
            return;
        }
        if (synchronizeAll) {
            String sql = alterTableSqlBuilder.buildSynchronize(entity, schema, current, diff, resolution);
            if (!sql.isEmpty()) {
                generatedSql.add(sql);
            }
            return;
        }
        if (diff.tableCommentChanged()) {
            generatedSql.add(alterTableSqlBuilder.buildComment(entity, schema));
        }
        for (DdlFieldMetadata field : diff.addedFields()) {
            generatedSql.add(alterTableSqlBuilder.buildAddColumn(entity, schema, field, resolution));
        }
        for (DdlFieldChange change : diff.changedFields()) {
            generatedSql.add(change.renamed()
                    ? alterTableSqlBuilder.buildChangeColumn(entity, schema, change, resolution)
                    : alterTableSqlBuilder.buildModifyColumn(entity, schema, change, resolution));
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

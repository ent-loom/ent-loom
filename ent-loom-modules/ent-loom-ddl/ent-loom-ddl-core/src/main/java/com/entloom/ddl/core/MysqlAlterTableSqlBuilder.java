package com.entloom.ddl.core;

import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.DdlFieldMetadata;
import com.entloom.ddl.api.DdlIndexMetadata;
import com.entloom.ddl.api.DdlTableSnapshot;
import com.entloom.ddl.enums.GenerationStrategy;
import java.util.ArrayList;
import java.util.List;

/**
 * MySQL ALTER TABLE SQL 生成器。
 */
public final class MysqlAlterTableSqlBuilder {
    private final MysqlCreateTableSqlBuilder createTableSqlBuilder;

    public MysqlAlterTableSqlBuilder() {
        this(new MysqlCreateTableSqlBuilder());
    }

    public MysqlAlterTableSqlBuilder(MysqlCreateTableSqlBuilder createTableSqlBuilder) {
        this.createTableSqlBuilder = createTableSqlBuilder == null
                ? new MysqlCreateTableSqlBuilder() : createTableSqlBuilder;
    }

    public String buildAddColumn(DdlEntityMetadata entity, String schema, DdlFieldMetadata field) {
        return prefix(entity, schema) + " ADD COLUMN " + createTableSqlBuilder.buildColumnSql(field);
    }

    public String buildModifyColumn(DdlEntityMetadata entity, String schema, DdlFieldChange change) {
        return prefix(entity, schema) + " MODIFY COLUMN "
                + MysqlCreateTableSqlBuilder.quote(change.desiredField().columnName()) + " "
                + createTableSqlBuilder.buildColumnDefinition(change.desiredField(), change.existingAutoIncrement());
    }

    public String buildChangeColumn(DdlEntityMetadata entity, String schema, DdlFieldChange change) {
        return prefix(entity, schema) + " CHANGE COLUMN "
                + MysqlCreateTableSqlBuilder.quote(change.existingColumnName()) + " "
                + MysqlCreateTableSqlBuilder.quote(change.desiredField().columnName()) + " "
                + createTableSqlBuilder.buildColumnDefinition(change.desiredField(), change.existingAutoIncrement());
    }

    public String buildAddIndex(DdlEntityMetadata entity, String schema, DdlIndexMetadata index) {
        return prefix(entity, schema) + " ADD " + createTableSqlBuilder.buildIndexSql(index);
    }

    public String buildComment(DdlEntityMetadata entity, String schema) {
        return prefix(entity, schema) + " COMMENT='" + escapeQuote(entity.comment()) + "'";
    }

    /**
     * 全量同步将单表差异合并为一条 ALTER，避免删除列后索引隐式消失，
     * 或修改主键、自增列时中间结构违反 MySQL 约束。
     */
    public String buildSynchronize(DdlEntityMetadata entity, String schema,
                                   DdlTableSnapshot current, DdlSchemaDiff diff) {
        List<String> clauses = new ArrayList<String>();
        for (String index : diff.removedIndexes()) {
            clauses.add("DROP INDEX " + MysqlCreateTableSqlBuilder.quote(index));
        }
        if (diff.primaryKeyChanged() && !current.primaryKeyColumns().isEmpty()) {
            clauses.add("DROP PRIMARY KEY");
        }
        for (DdlFieldMetadata field : diff.addedFields()) {
            clauses.add("ADD COLUMN " + createTableSqlBuilder.buildColumnSql(field));
        }
        for (DdlFieldChange change : diff.changedFields()) {
            DdlFieldMetadata desired = change.desiredField();
            boolean preserveAutoIncrement = change.existingAutoIncrement()
                    && desired.generationStrategy() != GenerationStrategy.NONE;
            String definition = MysqlCreateTableSqlBuilder.quote(desired.columnName()) + " "
                    + createTableSqlBuilder.buildColumnDefinition(desired, preserveAutoIncrement);
            clauses.add(change.renamed()
                    ? "CHANGE COLUMN " + MysqlCreateTableSqlBuilder.quote(change.existingColumnName()) + " " + definition
                    : "MODIFY COLUMN " + definition);
        }
        for (String column : diff.removedColumns()) {
            clauses.add("DROP COLUMN " + MysqlCreateTableSqlBuilder.quote(column));
        }
        if (diff.primaryKeyChanged()) {
            List<String> keys = new ArrayList<String>();
            for (DdlFieldMetadata field : entity.fields()) {
                if (field.persisted() && field.primaryKey()) {
                    keys.add(MysqlCreateTableSqlBuilder.quote(field.columnName()));
                }
            }
            if (!keys.isEmpty()) {
                clauses.add("ADD PRIMARY KEY (" + String.join(", ", keys) + ")");
            }
        }
        for (DdlIndexMetadata index : diff.addedIndexes()) {
            clauses.add("ADD " + createTableSqlBuilder.buildIndexSql(index));
        }
        if (diff.tableCommentChanged()) {
            clauses.add("COMMENT='" + escapeQuote(entity.comment()) + "'");
        }
        return clauses.isEmpty() ? "" : prefix(entity, schema) + " " + String.join(", ", clauses);
    }

    private static String prefix(DdlEntityMetadata entity, String schema) {
        return "ALTER TABLE " + MysqlCreateTableSqlBuilder.fullTableName(schema, entity.tableName());
    }

    private static String escapeQuote(String value) {
        return value == null ? "" : value.replace("'", "''");
    }
}

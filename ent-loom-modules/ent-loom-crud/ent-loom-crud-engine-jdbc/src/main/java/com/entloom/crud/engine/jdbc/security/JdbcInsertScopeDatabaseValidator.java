package com.entloom.crud.engine.jdbc.security;

import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;

/**
 * 校验 MySQL 范围字段不会被数据库结构改写。
 *
 * <p>首期 insert 的范围值必须完全由 DAO 绑定参数决定。无法从元数据证明触发器、生成列或
 * 自动更新规则不会改写范围值时，统一拒绝启动或 DAO 绑定。</p>
 */
public final class JdbcInsertScopeDatabaseValidator {
    private final DataSource dataSource;
    private final EntityMetaRegistry metaRegistry;

    public JdbcInsertScopeDatabaseValidator(DataSource dataSource, EntityMetaRegistry metaRegistry) {
        if (dataSource == null || metaRegistry == null) {
            throw new ValidationException("DataSource 和 EntityMetaRegistry 不能为空");
        }
        this.dataSource = dataSource;
        this.metaRegistry = metaRegistry;
    }

    /** 启动期校验标准注册表中的全部实体。 */
    public void validateOrThrow() {
        try (Connection connection = dataSource.getConnection()) {
            if (!isMySql(connection)) {
                return;
            }
            String schema = resolveSchema(connection);
            Collection<EntityMeta> metas = metaRegistry.getEntityMetas();
            if (metas == null || metas.isEmpty()) {
                throw new ValidationException("EntityMetaRegistry 必须提供完整实体元数据快照");
            }
            for (EntityMeta meta : metas) {
                validateEntity(connection, schema, meta);
            }
        } catch (ValidationException ex) {
            throw ex;
        } catch (UnsupportedOperationException ex) {
            ValidationException validationException = new ValidationException(ex.getMessage());
            validationException.initCause(ex);
            throw validationException;
        } catch (Exception ex) {
            ValidationException validationException = new ValidationException("无法校验 MySQL 范围字段数据库结构");
            validationException.initCause(ex);
            throw validationException;
        }
    }

    /**
     * 在 DAO 绑定范围时校验单个实体，供未经过 Starter 启动装配的显式 Factory 使用。
     *
     * @param meta 实体元数据
     */
    public void validateEntityOrThrow(EntityMeta meta) {
        if (meta == null) {
            throw new ValidationException("实体元数据不能为空");
        }
        try (Connection connection = dataSource.getConnection()) {
            if (!isMySql(connection)) {
                return;
            }
            validateEntity(connection, resolveSchema(connection), meta);
        } catch (ValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            ValidationException validationException = new ValidationException(
                "无法校验实体范围字段数据库结构: " + meta.getEntityName()
            );
            validationException.initCause(ex);
            throw validationException;
        }
    }

    private void validateEntity(Connection connection, String schema, EntityMeta meta) throws Exception {
        if (meta == null || meta.getFieldMetas() == null || meta.getFieldMetas().isEmpty()) {
            return;
        }
        Map<String, EntityFieldMeta> scopeFields = scopeFields(meta);
        if (scopeFields.isEmpty()) {
            return;
        }
        if (isBlank(schema)) {
            throw new ValidationException("MySQL 当前连接未选定数据库: " + meta.getEntityName());
        }

        Map<String, ColumnState> columns = readColumns(connection, schema, meta.getTable());
        // 表结构可能由应用启动后的迁移任务创建；不存在的表交给 DDL/迁移阶段处理。
        if (columns.isEmpty()) {
            return;
        }
        for (Map.Entry<String, EntityFieldMeta> entry : scopeFields.entrySet()) {
            EntityFieldMeta field = entry.getValue();
            String columnName = field.getColumnName();
            ColumnState column = columns.get(normalize(columnName));
            if (column == null) {
                throw new ValidationException("范围字段对应数据库列不存在: "
                    + meta.getEntityName() + "." + entry.getKey() + " -> " + columnName);
            }
            if (column.generated) {
                throw new ValidationException("范围字段不能使用生成列: "
                    + meta.getEntityName() + "." + entry.getKey());
            }
            if (column.autoIncrement) {
                throw new ValidationException("范围字段不能使用 AUTO_INCREMENT: "
                    + meta.getEntityName() + "." + entry.getKey());
            }
            if (column.onUpdate) {
                throw new ValidationException("范围字段不能使用 ON UPDATE 自动改写: "
                    + meta.getEntityName() + "." + entry.getKey());
            }
        }
        if (!hasTriggerMetadataPrivilege(connection, schema, meta.getTable())) {
            throw new ValidationException("当前 MySQL 账号无法可靠读取范围表触发器元数据，"
                + "请授予目标表 TRIGGER/ALL 权限或配置具备元数据可见性的校验连接: "
                + meta.getEntityName() + " -> " + meta.getTable());
        }
        if (hasTrigger(connection, schema, meta.getTable())) {
            throw new ValidationException("包含范围字段的实体表不能使用触发器: "
                + meta.getEntityName() + " -> " + meta.getTable());
        }
    }

    private Map<String, EntityFieldMeta> scopeFields(EntityMeta meta) {
        Map<String, EntityFieldMeta> result = new HashMap<String, EntityFieldMeta>();
        for (Map.Entry<String, EntityFieldMeta> entry : meta.getFieldMetas().entrySet()) {
            if (entry.getValue() != null && entry.getValue().isScopeField()) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private Map<String, ColumnState> readColumns(Connection connection, String schema, String table)
        throws Exception {
        if (isBlank(table)) {
            throw new ValidationException("实体表名不能为空");
        }
        String sql = "select column_name, extra, generation_expression"
            + " from information_schema.columns where table_schema = ? and table_name = ?";
        Map<String, ColumnState> result = new HashMap<String, ColumnState>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String name = rows.getString("column_name");
                    String extra = rows.getString("extra");
                    String generationExpression = rows.getString("generation_expression");
                    result.put(normalize(name), new ColumnState(
                        isGenerated(extra, generationExpression),
                        containsIgnoreCase(extra, "auto_increment"),
                        containsIgnoreCase(extra, "on update")
                    ));
                }
            }
        }
        return result;
    }

    private boolean hasTrigger(Connection connection, String schema, String table) throws Exception {
        String sql = "select trigger_name from information_schema.triggers"
            + " where trigger_schema = ? and event_object_table = ? limit 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private boolean hasTriggerMetadataPrivilege(Connection connection, String schema, String table)
        throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("show grants");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String grant = rows.getString(1);
                if (grant != null && grantsTriggerFor(grant, schema, table)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean grantsTriggerFor(String grant, String schema, String table) {
        String normalized = grant.toLowerCase(Locale.ROOT)
            .replace("`", "")
            .replaceAll("\\s+", " ");
        if (!normalized.contains("trigger") && !normalized.contains("all privileges")) {
            return false;
        }
        String schemaName = normalize(schema);
        String tableName = normalize(table);
        return normalized.contains(" on *.*")
            || normalized.contains(" on " + schemaName + ".*")
            || normalized.contains(" on " + schemaName + "." + tableName);
    }

    private String resolveSchema(Connection connection) throws Exception {
        String catalog = connection.getCatalog();
        if (!isBlank(catalog)) {
            return catalog;
        }
        try (PreparedStatement statement = connection.prepareStatement("select database()")) {
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }

    private boolean isMySql(Connection connection) throws Exception {
        if (connection == null || connection.getMetaData() == null) {
            return false;
        }
        String productName = connection.getMetaData().getDatabaseProductName();
        return containsIgnoreCase(productName, "mysql");
    }

    static boolean isGenerated(String extra, String generationExpression) {
        return !trim(generationExpression).isEmpty()
            || containsIgnoreCase(extra, "virtual generated")
            || containsIgnoreCase(extra, "stored generated");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean containsIgnoreCase(String value, String fragment) {
        return value != null && fragment != null
            && value.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
    }

    private static final class ColumnState {
        private final boolean generated;
        private final boolean autoIncrement;
        private final boolean onUpdate;

        private ColumnState(boolean generated, boolean autoIncrement, boolean onUpdate) {
            this.generated = generated;
            this.autoIncrement = autoIncrement;
            this.onUpdate = onUpdate;
        }
    }
}

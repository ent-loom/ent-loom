package com.entloom.crud.engine.jdbc.dao;

import com.entloom.crud.core.capability.command.patch.NormalizedUpdatePatch;
import com.entloom.crud.core.capability.command.patch.UpdatePatch;
import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.capability.dao.InsertConstraintValueBinder;
import com.entloom.crud.core.capability.dao.RowConstraint;
import com.entloom.crud.core.capability.dao.RowConstraintNormalizer;
import com.entloom.crud.core.exception.EntityDaoConstraintException;
import com.entloom.crud.core.exception.EntityDaoPersistenceException;
import com.entloom.crud.core.exception.EntityDaoWriteMissException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.context.DefaultExecutionContext;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.security.GuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.dialect.JdbcDialect;
import com.entloom.crud.engine.jdbc.dialect.StandardJdbcDialect;
import com.entloom.crud.engine.jdbc.sql.JdbcLogicDeleteValues;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 单表 JDBC 实体 DAO。
 */
final class JdbcEntityDao<T, ID> implements EntityDao<T, ID> {
    private final EntityType<T, ID> entityType;
    private final EntityMeta meta;
    private final RowConstraint scope;
    private final GuardedSqlExecutor guardedSqlExecutor;
    private final JdbcEntityPredicateCompiler predicateCompiler;
    private final JdbcDialect dialect;

    JdbcEntityDao(
        EntityType<T, ID> entityType,
        EntityMeta meta,
        RowConstraint scope,
        GuardedSqlExecutor guardedSqlExecutor
    ) {
        this(
            entityType,
            meta,
            scope,
            guardedSqlExecutor,
            new JdbcEntityPredicateCompiler(),
            StandardJdbcDialect.GENERIC
        );
    }

    JdbcEntityDao(
        EntityType<T, ID> entityType,
        EntityMeta meta,
        RowConstraint scope,
        GuardedSqlExecutor guardedSqlExecutor,
        JdbcEntityPredicateCompiler predicateCompiler
    ) {
        this(
            entityType,
            meta,
            scope,
            guardedSqlExecutor,
            predicateCompiler,
            StandardJdbcDialect.GENERIC
        );
    }

    JdbcEntityDao(
        EntityType<T, ID> entityType,
        EntityMeta meta,
        RowConstraint scope,
        GuardedSqlExecutor guardedSqlExecutor,
        JdbcEntityPredicateCompiler predicateCompiler,
        JdbcDialect dialect
    ) {
        this.entityType = entityType;
        this.meta = meta;
        this.scope = scope;
        this.guardedSqlExecutor = guardedSqlExecutor;
        this.predicateCompiler = predicateCompiler;
        this.dialect = dialect == null ? StandardJdbcDialect.GENERIC : dialect;
    }

    @Override
    public Optional<T> findById(ID id) {
        Object normalizedId = normalizeId(id);
        JdbcEntityPredicateCompiler.CompiledWhere where = predicateCompiler.byId(
            meta, meta.getIdField(), normalizedId, scope
        );
        String sql = "select " + selectColumns() + " from " + table()
            + " where " + where.getSql();
        List<Map<String, Object>> rows = guardedSqlExecutor.queryForList(
            sql, where.getArgs(), context("findById")
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        if (rows.size() > 1) {
            throw new EntityDaoPersistenceException("按主键查询命中多行: " + meta.getEntityName());
        }
        return Optional.of(mapRow(rows.get(0)));
    }

    @Override
    public ID insert(T entity) {
        if (entity == null || !entityType.getEntityClass().isInstance(entity)) {
            throw new ValidationException("insert 实体类型不一致: " + meta.getEntityName());
        }
        Map<String, Object> values = readEntityValues(entity);
        Object suppliedId = values.get(meta.getIdField());
        boolean generatedId = meta.getIdPolicy() == EntityIdPolicy.GENERATED;
        Object id = null;
        if (generatedId) {
            if (suppliedId != null) {
                throw new ValidationException("GENERATED 主键实体不能显式指定主键: " + meta.getEntityName());
            }
            values.remove(meta.getIdField());
        } else {
            id = normalizeId(suppliedId);
            values.put(meta.getIdField(), id);
        }
        Map<String, Object> scopedValues = new LinkedHashMap<String, Object>(
            InsertConstraintValueBinder.bind(scope, meta, values)
        );
        applyLogicDeleteInitialValue(scopedValues);

        List<String> fields = insertFields(scopedValues);
        if (fields.isEmpty()) {
            throw new ValidationException("insert 没有可写字段: " + meta.getEntityName());
        }
        List<Object> args = new ArrayList<Object>();
        List<String> columns = new ArrayList<String>();
        for (String field : fields) {
            columns.add(column(field));
            args.add(normalizeFieldValue(field, scopedValues.get(field)));
        }
        predicateCompiler.validateParameterCount(args.size());
        String sql = "insert into " + table() + " (" + String.join(",", columns) + ") values ("
            + placeholders(fields.size()) + ")";
        try {
            if (generatedId) {
                Object generated = guardedSqlExecutor.insertAndReturnGeneratedKey(
                    sql, args, context("insert")
                );
                try {
                    id = normalizeId(generated);
                } catch (ValidationException ex) {
                    throw new EntityDaoPersistenceException("数据库生成主键缺失或类型不匹配: " + meta.getEntityName(), ex);
                }
                writeField(entity, meta.getIdField(), id);
            } else {
                int rows = guardedSqlExecutor.update(sql, args, context("insert"));
                if (rows != 1) {
                    throw new EntityDaoPersistenceException("insert 影响行数不是 1: " + rows);
                }
            }
        } catch (DataIntegrityViolationException ex) {
            throw new EntityDaoConstraintException("insert 违反数据约束: " + meta.getEntityName(), ex);
        }
        return entityType.getIdClass().cast(id);
    }

    @Override
    public int updateById(ID id, UpdatePatch<T> patch) {
        Object normalizedId = normalizeId(id);
        NormalizedUpdatePatch normalizedPatch = NormalizedUpdatePatch.from(patch, meta);
        if (!normalizedId.equals(normalizeId(normalizedPatch.getId()))) {
            throw new ValidationException("Patch 主键与 updateById 参数不一致");
        }
        List<String> fields = new ArrayList<String>(normalizedPatch.getChanges().keySet());
        List<Object> args = new ArrayList<Object>();
        List<String> assignments = new ArrayList<String>();
        for (String field : fields) {
            assignments.add(column(field) + " = ?");
            args.add(normalizeFieldValue(field, normalizedPatch.getChanges().get(field)));
        }
        if (assignments.isEmpty()) {
            throw new ValidationException("Patch 没有可更新字段");
        }
        JdbcEntityPredicateCompiler.CompiledWhere where = predicateCompiler.byId(
            meta, meta.getIdField(), normalizedId, scope
        );
        args.addAll(where.getArgs());
        predicateCompiler.validateParameterCount(args.size());
        String sql = "update " + table() + " set " + String.join(",", assignments)
            + " where " + where.getSql();
        try {
            int rows = guardedSqlExecutor.update(sql, args, context("updateById"));
            return requireSingleWrite(rows, "updateById");
        } catch (DataIntegrityViolationException ex) {
            throw new EntityDaoConstraintException("updateById 违反数据约束: " + meta.getEntityName(), ex);
        }
    }

    @Override
    public int deleteById(ID id) {
        Object normalizedId = normalizeId(id);
        JdbcEntityPredicateCompiler.CompiledWhere where = predicateCompiler.byId(
            meta, meta.getIdField(), normalizedId, scope
        );
        List<Object> args = new ArrayList<Object>();
        String sql;
        if (hasLogicDelete()) {
            sql = "update " + table() + " set "
                + column(meta.getLogicDeleteField()) + " = ? where " + where.getSql();
            args.add(normalizeFieldValue(
                meta.getLogicDeleteField(), JdbcLogicDeleteValues.deleted(meta)
            ));
        } else {
            sql = "delete from " + table() + " where " + where.getSql();
        }
        args.addAll(where.getArgs());
        predicateCompiler.validateParameterCount(args.size());
        try {
            int rows = guardedSqlExecutor.update(sql, args, context("deleteById"));
            return requireSingleWrite(rows, "deleteById");
        } catch (DataIntegrityViolationException ex) {
            throw new EntityDaoConstraintException("deleteById 违反数据约束: " + meta.getEntityName(), ex);
        }
    }

    private int requireSingleWrite(int rows, String operation) {
        if (rows == 0) {
            throw new EntityDaoWriteMissException("目标不存在或不可写: " + meta.getEntityName());
        }
        if (rows != 1) {
            throw new EntityDaoPersistenceException(operation + " 影响行数不是 1: " + rows);
        }
        return 1;
    }

    private boolean hasLogicDelete() {
        return meta.getLogicDeleteField() != null && !meta.getLogicDeleteField().trim().isEmpty();
    }

    private void applyLogicDeleteInitialValue(Map<String, Object> values) {
        if (!hasLogicDelete()) {
            return;
        }
        String field = meta.getLogicDeleteField();
        Object supplied = values.get(field);
        Object expected = normalizeFieldValue(field, JdbcLogicDeleteValues.notDeleted(meta));
        if (supplied != null && !expected.equals(normalizeFieldValue(field, supplied))) {
            throw new ValidationException("insert 不能覆盖逻辑删除初始值: " + field);
        }
        values.put(field, expected);
    }

    private List<String> insertFields(Map<String, Object> values) {
        List<String> fields = new ArrayList<String>();
        for (Map.Entry<String, EntityFieldMeta> entry : meta.getFieldMetas().entrySet()) {
            if (entry.getValue().isRelation()) {
                continue;
            }
            if (values.containsKey(entry.getKey()) && (values.get(entry.getKey()) != null
                || entry.getKey().equals(meta.getIdField())
                || entry.getValue().isScopeField()
                || entry.getKey().equals(meta.getLogicDeleteField()))) {
                fields.add(entry.getKey());
            }
        }
        return fields;
    }

    private Map<String, Object> readEntityValues(Object entity) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, EntityFieldMeta> entry : meta.getFieldMetas().entrySet()) {
            if (!entry.getValue().isRelation()) {
                values.put(entry.getKey(), readField(entity, entry.getKey()));
            }
        }
        return values;
    }

    private Object readField(Object entity, String fieldName) {
        Field field = resolveField(entity.getClass(), fieldName);
        if (field == null) {
            throw new ValidationException("实体字段不存在: " + fieldName);
        }
        try {
            field.setAccessible(true);
            return field.get(entity);
        } catch (IllegalAccessException ex) {
            throw new ValidationException("实体字段无法读取: " + fieldName);
        }
    }

    private void writeField(Object entity, String fieldName, Object value) {
        Field field = resolveField(entity.getClass(), fieldName);
        if (field == null) {
            throw new EntityDaoPersistenceException("实体字段不存在，无法回填主键: " + fieldName);
        }
        try {
            field.setAccessible(true);
            field.set(entity, value);
        } catch (IllegalAccessException | IllegalArgumentException ex) {
            throw new EntityDaoPersistenceException("实体主键无法回填: " + fieldName, ex);
        }
    }

    private T mapRow(Map<String, Object> row) {
        try {
            T target = entityType.getEntityClass().getDeclaredConstructor().newInstance();
            for (Map.Entry<String, EntityFieldMeta> entry : meta.getFieldMetas().entrySet()) {
                if (entry.getValue().isRelation()) {
                    continue;
                }
                String fieldName = entry.getKey();
                String column = meta.resolveColumn(fieldName);
                Object raw = row.containsKey(column) ? row.get(column) : row.get(fieldName);
                Field field = resolveField(entityType.getEntityClass(), fieldName);
                if (field == null || raw == null) {
                    continue;
                }
                field.setAccessible(true);
                field.set(target, adaptValue(field.getType(), raw));
            }
            return target;
        } catch (ReflectiveOperationException ex) {
            throw new EntityDaoPersistenceException("查询结果映射失败: " + meta.getEntityName(), ex);
        }
    }

    private Object adaptValue(Class<?> type, Object raw) {
        Class<?> target = wrap(type);
        if (target.isInstance(raw)) {
            return raw;
        }
        return RowConstraintNormalizer.normalizeValue(
            new EntityFieldMeta("value", target, "value", true, false, true, true), raw
        );
    }

    private Object normalizeId(Object id) {
        if (id == null) {
            throw new ValidationException("主键不能为空: " + meta.getEntityName());
        }
        EntityFieldMeta field = meta.resolveFieldMeta(meta.getIdField());
        Object normalized = RowConstraintNormalizer.normalizeValue(field, id);
        if (!wrap(entityType.getIdClass()).isInstance(normalized)) {
            throw new ValidationException("主键类型不匹配: " + id);
        }
        return normalized;
    }

    private Object normalizeFieldValue(String fieldName, Object value) {
        if (value == null) {
            return null;
        }
        return JdbcEntityValueBinder.normalize(meta.resolveFieldMeta(fieldName), value);
    }

    private String selectColumns() {
        List<String> columns = new ArrayList<String>();
        for (Map.Entry<String, EntityFieldMeta> entry : meta.getFieldMetas().entrySet()) {
            if (!entry.getValue().isRelation()) {
                columns.add(column(entry.getKey()) + " as " + dialect.quoteIdentifier(entry.getKey()));
            }
        }
        return String.join(",", columns);
    }

    private String placeholders(int size) {
        List<String> placeholders = new ArrayList<String>();
        for (int i = 0; i < size; i++) {
            placeholders.add("?");
        }
        return String.join(",", placeholders);
    }

    private String table() {
        return dialect.quoteIdentifier(meta.getTable());
    }

    private String column(String fieldName) {
        return dialect.quoteIdentifier(meta.resolveColumn(fieldName));
    }

    private DefaultExecutionContext context(String operation) {
        DefaultExecutionContext context = new DefaultExecutionContext(
            meta.getEntityName() + "|DAO|" + operation,
            null
        );
        context.getAttributes().put("operationDomain", "ENTITY_DAO");
        context.getAttributes().put("operation", operation);
        context.getAttributes().put("phase", "main");
        return context;
    }

    private Field resolveField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private Class<?> wrap(Class<?> type) {
        if (type == null || !type.isPrimitive()) {
            return type;
        }
        if (type == Long.TYPE) {
            return Long.class;
        }
        if (type == Integer.TYPE) {
            return Integer.class;
        }
        if (type == Short.TYPE) {
            return Short.class;
        }
        if (type == Byte.TYPE) {
            return Byte.class;
        }
        if (type == Boolean.TYPE) {
            return Boolean.class;
        }
        if (type == Double.TYPE) {
            return Double.class;
        }
        if (type == Float.TYPE) {
            return Float.class;
        }
        if (type == Character.TYPE) {
            return Character.class;
        }
        return type;
    }
}

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
import com.entloom.crud.core.foundation.write.CrudWriteTransactionCallback;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionExecutor;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionPolicy;
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
import java.util.Set;
import java.util.LinkedHashSet;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 单表 JDBC 实体 DAO。
 */
final class JdbcEntityDao<T, ID> implements EntityDao<T, ID> {
    static final int DEFAULT_MAX_BATCH_SIZE = JdbcEntityDaoFactory.DEFAULT_MAX_BATCH_SIZE;
    private static final String VERSION_FIELD = "version";
    private final EntityType<T, ID> entityType;
    private final EntityMeta meta;
    private final RowConstraint scope;
    private final GuardedSqlExecutor guardedSqlExecutor;
    private final JdbcEntityPredicateCompiler predicateCompiler;
    private final JdbcDialect dialect;
    private final CrudWriteTransactionExecutor transactionExecutor;
    private final int maxBatchSize;

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
            StandardJdbcDialect.GENERIC,
            null,
            DEFAULT_MAX_BATCH_SIZE
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
            StandardJdbcDialect.GENERIC,
            null,
            DEFAULT_MAX_BATCH_SIZE
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
        this(
            entityType,
            meta,
            scope,
            guardedSqlExecutor,
            predicateCompiler,
            dialect,
            null,
            DEFAULT_MAX_BATCH_SIZE
        );
    }

    JdbcEntityDao(
        EntityType<T, ID> entityType,
        EntityMeta meta,
        RowConstraint scope,
        GuardedSqlExecutor guardedSqlExecutor,
        JdbcEntityPredicateCompiler predicateCompiler,
        JdbcDialect dialect,
        CrudWriteTransactionExecutor transactionExecutor,
        int maxBatchSize
    ) {
        if (maxBatchSize <= 0) {
            throw new ValidationException("DAO 批量数量上限必须大于 0");
        }
        this.entityType = entityType;
        this.meta = meta;
        this.scope = scope;
        this.guardedSqlExecutor = guardedSqlExecutor;
        this.predicateCompiler = predicateCompiler;
        this.dialect = dialect == null ? StandardJdbcDialect.GENERIC : dialect;
        this.transactionExecutor = transactionExecutor;
        this.maxBatchSize = maxBatchSize;
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
    public List<T> findAllById(java.util.Collection<ID> ids) {
        List<Object> normalizedIds = normalizeDistinctIds(ids);
        if (normalizedIds.isEmpty()) {
            return Collections.emptyList();
        }
        JdbcEntityPredicateCompiler.CompiledWhere where = predicateCompiler.byIds(
            meta, meta.getIdField(), normalizedIds, scope
        );
        String sql = "select " + selectColumns() + " from " + table()
            + " where " + where.getSql();
        List<Map<String, Object>> rows = guardedSqlExecutor.queryForList(
            sql, where.getArgs(), context("findAllById")
        );
        Map<Object, T> found = new LinkedHashMap<Object, T>();
        for (Map<String, Object> row : rows) {
            Object rowId = normalizeId(rowValue(row, meta.getIdField()));
            if (found.put(rowId, mapRow(row)) != null) {
                throw new EntityDaoPersistenceException("按主键批量查询命中重复记录: " + meta.getEntityName());
            }
        }
        List<T> result = new ArrayList<T>();
        for (Object id : normalizedIds) {
            T entity = found.get(id);
            if (entity != null) {
                result.add(entity);
            }
        }
        return result;
    }

    @Override
    public Map<ID, T> findAllByIdAsMap(java.util.Collection<ID> ids) {
        Map<ID, T> result = new LinkedHashMap<ID, T>();
        for (T entity : findAllById(ids)) {
            ID id = entityType.getIdClass().cast(normalizeId(readField(entity, meta.getIdField())));
            result.put(id, entity);
        }
        return result;
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
        applyVersionInitialValue(scopedValues, entity);

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
            if (hasVersion()) {
                writeField(entity, VERSION_FIELD, versionFieldValue(0L));
            }
        } catch (DataIntegrityViolationException ex) {
            throw new EntityDaoConstraintException("insert 违反数据约束: " + meta.getEntityName(), ex);
        }
        return entityType.getIdClass().cast(id);
    }

    @Override
    public List<ID> insertAll(java.util.Collection<T> entities) {
        final List<T> batch = validateBatch(entities, "insertAll");
        if (batch.isEmpty()) {
            return Collections.emptyList();
        }
        validateInsertIdentities(batch);
        return executeBatch(new CrudWriteTransactionCallback<List<ID>>() {
            @Override
            public List<ID> execute() {
                List<ID> ids = new ArrayList<ID>(batch.size());
                for (T entity : batch) {
                    ids.add(insert(entity));
                }
                return ids;
            }
        });
    }

    @Override
    public int update(T entity) {
        return updateEntity(entity);
    }

    @Override
    public int updateById(T entity) {
        return updateEntity(entity);
    }

    @Override
    public int updateById(ID id, UpdatePatch<T> patch) {
        Object normalizedId = normalizeId(id);
        NormalizedUpdatePatch normalizedPatch = NormalizedUpdatePatch.from(patch, meta);
        if (!normalizedId.equals(normalizeId(normalizedPatch.getId()))) {
            throw new ValidationException("Patch 主键与 updateById 参数不一致");
        }
        Long expectedVersion = expectedPatchVersion(patch);
        return executeUpdate(
            normalizedId,
            normalizedPatch.getChanges(),
            expectedVersion,
            patch.getEntity(),
            "updateById"
        );
    }

    @Override
    public int updateAll(java.util.Collection<T> entities) {
        final List<T> batch = validateBatch(entities, "updateAll");
        if (batch.isEmpty()) {
            return 0;
        }
        validateEntityIdentities(batch, "updateAll");
        return executeBatch(new CrudWriteTransactionCallback<Integer>() {
            @Override
            public Integer execute() {
                int rows = 0;
                for (T entity : batch) {
                    rows += update(entity);
                }
                return Integer.valueOf(rows);
            }
        }).intValue();
    }

    @Override
    public int deleteById(ID id) {
        if (hasVersion()) {
            throw new ValidationException("启用 version 的实体必须携带预期版本删除: " + meta.getEntityName());
        }
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
            return requireAtMostOneWrite(rows, "deleteById");
        } catch (DataIntegrityViolationException ex) {
            throw new EntityDaoConstraintException("deleteById 违反数据约束: " + meta.getEntityName(), ex);
        }
    }

    @Override
    public int delete(T entity) {
        validateEntity(entity, "delete");
        ID id = typedId(normalizeId(readField(entity, meta.getIdField())));
        if (hasVersion()) {
            return deleteById(id, readExpectedVersion(entity));
        }
        return deleteById(id);
    }

    @SuppressWarnings("unchecked")
    private ID typedId(Object id) {
        return (ID) id;
    }

    @Override
    public int deleteById(ID id, long expectedVersion) {
        requireVersionSupport();
        Object normalizedId = normalizeId(id);
        Object normalizedVersion = normalizeVersion(Long.valueOf(expectedVersion));
        JdbcEntityPredicateCompiler.CompiledWhere where = predicateCompiler.byId(
            meta, meta.getIdField(), normalizedId, scope
        );
        List<Object> args = new ArrayList<Object>();
        String sql;
        if (hasLogicDelete()) {
            sql = "update " + table() + " set "
                + column(meta.getLogicDeleteField()) + " = ?,"
                + column(VERSION_FIELD) + " = " + column(VERSION_FIELD) + " + 1 where "
                + where.getSql() + " and " + column(VERSION_FIELD) + " = ?";
            args.add(normalizeFieldValue(
                meta.getLogicDeleteField(), JdbcLogicDeleteValues.deleted(meta)
            ));
        } else {
            sql = "delete from " + table() + " where " + where.getSql()
                + " and " + column(VERSION_FIELD) + " = ?";
        }
        args.addAll(where.getArgs());
        args.add(normalizedVersion);
        predicateCompiler.validateParameterCount(args.size());
        try {
            int rows = guardedSqlExecutor.update(sql, args, context("deleteByIdWithVersion"));
            return requireSingleWrite(rows, "deleteByIdWithVersion");
        } catch (DataIntegrityViolationException ex) {
            throw new EntityDaoConstraintException("带版本 deleteById 违反数据约束: " + meta.getEntityName(), ex);
        }
    }

    @Override
    public int deleteAll(java.util.Collection<T> entities) {
        final List<T> batch = validateBatch(entities, "deleteAll");
        if (batch.isEmpty()) {
            return 0;
        }
        validateEntityIdentities(batch, "deleteAll");
        return executeBatch(new CrudWriteTransactionCallback<Integer>() {
            @Override
            public Integer execute() {
                int rows = 0;
                for (T entity : batch) {
                    rows += delete(entity);
                }
                return Integer.valueOf(rows);
            }
        }).intValue();
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

    private int requireAtMostOneWrite(int rows, String operation) {
        if (rows < 0 || rows > 1) {
            throw new EntityDaoPersistenceException(operation + " 影响行数不是 0 或 1: " + rows);
        }
        return rows;
    }

    private int updateEntity(T entity) {
        validateEntity(entity, "update");
        Object id = normalizeId(readField(entity, meta.getIdField()));
        Map<String, Object> values = readEntityValues(entity);
        Map<String, Object> changes = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, EntityFieldMeta> entry : meta.getFieldMetas().entrySet()) {
            if (isOrdinaryWritableField(entry.getKey(), entry.getValue())) {
                changes.put(entry.getKey(), values.get(entry.getKey()));
            }
        }
        Long expectedVersion = hasVersion() ? readExpectedVersion(entity) : null;
        return executeUpdate(id, changes, expectedVersion, entity, "update");
    }

    private int executeUpdate(
        Object id,
        Map<String, Object> changes,
        Long expectedVersion,
        T entityToBackfill,
        String operation
    ) {
        if (changes == null || changes.isEmpty()) {
            throw new ValidationException("更新没有可写字段: " + meta.getEntityName());
        }
        if (hasVersion()) {
            requireVersionSupport();
            if (expectedVersion == null) {
                throw new ValidationException("启用 version 的实体更新必须携带预期版本: " + meta.getEntityName());
            }
        } else if (expectedVersion != null) {
            throw new ValidationException("实体未启用 version，不能携带预期版本: " + meta.getEntityName());
        }

        List<Object> args = new ArrayList<Object>();
        List<String> assignments = new ArrayList<String>();
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            String field = entry.getKey();
            EntityFieldMeta fieldMeta = meta.resolveFieldMeta(field);
            if (!isOrdinaryWritableField(field, fieldMeta)) {
                throw new ValidationException("不允许更新字段: " + field);
            }
            assignments.add(column(field) + " = ?");
            args.add(normalizeFieldValue(field, entry.getValue()));
        }
        if (hasVersion()) {
            assignments.add(column(VERSION_FIELD) + " = " + column(VERSION_FIELD) + " + 1");
        }
        JdbcEntityPredicateCompiler.CompiledWhere where = predicateCompiler.byId(
            meta, meta.getIdField(), normalizeId(id), scope
        );
        String whereSql = where.getSql();
        args.addAll(where.getArgs());
        if (expectedVersion != null) {
            whereSql += " and " + column(VERSION_FIELD) + " = ?";
            args.add(normalizeVersion(expectedVersion));
        }
        predicateCompiler.validateParameterCount(args.size());
        String sql = "update " + table() + " set " + String.join(",", assignments)
            + " where " + whereSql;
        try {
            int rows = guardedSqlExecutor.update(sql, args, context(operation));
            int result = requireSingleWrite(rows, operation);
            if (hasVersion() && entityToBackfill != null) {
                writeField(entityToBackfill, VERSION_FIELD, versionFieldValue(expectedVersion.longValue() + 1L));
            }
            return result;
        } catch (DataIntegrityViolationException ex) {
            throw new EntityDaoConstraintException(operation + " 违反数据约束: " + meta.getEntityName(), ex);
        }
    }

    private Long expectedPatchVersion(UpdatePatch<T> patch) {
        if (!hasVersion()) {
            if (patch.getExpectedVersion() != null) {
                throw new ValidationException("实体未启用 version，不能携带预期版本: " + meta.getEntityName());
            }
            return null;
        }
        Long expected = patch.getExpectedVersion();
        if (expected == null && patch.hasField(VERSION_FIELD)) {
            expected = patch.get(VERSION_FIELD, Long.class);
        }
        if (expected == null) {
            throw new ValidationException("启用 version 的 Patch 必须携带预期版本: " + meta.getEntityName());
        }
        return expected;
    }

    private boolean hasVersion() {
        EntityFieldMeta field = meta.resolveFieldMeta(VERSION_FIELD);
        return field != null && !field.isRelation();
    }

    private void requireVersionSupport() {
        if (!hasVersion()) {
            throw new ValidationException("实体未启用 version: " + meta.getEntityName());
        }
        Class<?> type = wrap(meta.resolveFieldMeta(VERSION_FIELD).getJavaType());
        if (!Number.class.isAssignableFrom(type)) {
            throw new ValidationException("version 字段必须是数字类型: " + meta.getEntityName());
        }
    }

    private Object normalizeVersion(Long version) {
        requireVersionSupport();
        return normalizeFieldValue(VERSION_FIELD, version);
    }

    private Object versionFieldValue(long value) {
        requireVersionSupport();
        return adaptValue(meta.resolveFieldMeta(VERSION_FIELD).getJavaType(), Long.valueOf(value));
    }

    private Long readExpectedVersion(Object entity) {
        requireVersionSupport();
        Object value = readField(entity, VERSION_FIELD);
        if (value == null) {
            throw new ValidationException("启用 version 的实体必须携带预期版本: " + meta.getEntityName());
        }
        try {
            return readVersionNumber(value);
        } catch (NumberFormatException ex) {
            throw new ValidationException("version 不是有效数字: " + value);
        }
    }

    private void applyVersionInitialValue(Map<String, Object> values, T entity) {
        if (!hasVersion()) {
            return;
        }
        requireVersionSupport();
        Object supplied = values.get(VERSION_FIELD);
        if (supplied != null && !Long.valueOf(0L).equals(readVersionNumber(supplied))) {
            throw new ValidationException("insert 不能覆盖 version 初始值: " + meta.getEntityName());
        }
        values.put(VERSION_FIELD, normalizeVersion(Long.valueOf(0L)));
    }

    private Long readVersionNumber(Object value) {
        try {
            if (value instanceof Number) {
                return Long.valueOf(((Number) value).longValue());
            }
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new ValidationException("version 不是有效数字: " + value);
        }
    }

    private boolean isOrdinaryWritableField(String field, EntityFieldMeta fieldMeta) {
        return fieldMeta != null
            && !field.equals(meta.getIdField())
            && !field.equals(meta.getLogicDeleteField())
            && !VERSION_FIELD.equals(field)
            && !fieldMeta.isRelation()
            && fieldMeta.isWritable()
            && !fieldMeta.isScopeField()
            && !fieldMeta.isImmutable();
    }

    private void validateEntity(T entity, String operation) {
        if (entity == null || !entityType.getEntityClass().isInstance(entity)) {
            throw new ValidationException(operation + " 实体类型不一致: " + meta.getEntityName());
        }
    }

    private List<T> validateBatch(java.util.Collection<T> entities, String operation) {
        if (entities == null) {
            throw new ValidationException(operation + " 集合不能为空");
        }
        if (entities.size() > maxBatchSize) {
            throw new ValidationException(operation + " 数量超过上限 " + maxBatchSize);
        }
        List<T> result = new ArrayList<T>(entities.size());
        for (T entity : entities) {
            if (entity == null) {
                throw new ValidationException(operation + " 不允许包含 null 实体");
            }
            result.add(entity);
        }
        return result;
    }

    private void validateInsertIdentities(List<T> entities) {
        Set<Object> ids = new LinkedHashSet<Object>();
        boolean generated = meta.getIdPolicy() == EntityIdPolicy.GENERATED;
        for (T entity : entities) {
            validateEntity(entity, "insertAll");
            Object id = readField(entity, meta.getIdField());
            if (generated) {
                if (id != null) {
                    throw new ValidationException("GENERATED 主键实体不能显式指定主键: " + meta.getEntityName());
                }
            } else {
                Object normalized = normalizeId(id);
                if (!ids.add(normalized)) {
                    throw new ValidationException("insertAll 包含重复主键: " + normalized);
                }
            }
        }
    }

    private void validateEntityIdentities(List<T> entities, String operation) {
        Set<Object> ids = new LinkedHashSet<Object>();
        for (T entity : entities) {
            validateEntity(entity, operation);
            Object id = normalizeId(readField(entity, meta.getIdField()));
            if (!ids.add(id)) {
                throw new ValidationException(operation + " 包含重复主键: " + id);
            }
        }
    }

    private <R> R executeBatch(CrudWriteTransactionCallback<R> callback) {
        if (transactionExecutor == null) {
            throw new ValidationException(
                "JDBC DAO 批量写入必须配置 CrudWriteTransactionExecutor，避免部分提交"
            );
        }
        return transactionExecutor.execute(CrudWriteTransactionPolicy.SINGLE_TRANSACTION, callback);
    }

    private List<Object> normalizeDistinctIds(java.util.Collection<ID> ids) {
        if (ids == null) {
            throw new ValidationException("主键集合不能为空");
        }
        if (ids.size() > maxBatchSize) {
            throw new ValidationException("主键数量超过上限 " + maxBatchSize);
        }
        Set<Object> distinct = new LinkedHashSet<Object>();
        for (ID id : ids) {
            distinct.add(normalizeId(id));
        }
        return new ArrayList<Object>(distinct);
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

    private Object rowValue(Map<String, Object> row, String fieldName) {
        String columnName = meta.resolveColumn(fieldName);
        if (row.containsKey(columnName)) {
            return row.get(columnName);
        }
        if (row.containsKey(fieldName)) {
            return row.get(fieldName);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && (entry.getKey().equalsIgnoreCase(columnName)
                || entry.getKey().equalsIgnoreCase(fieldName))) {
                return entry.getValue();
            }
        }
        return null;
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

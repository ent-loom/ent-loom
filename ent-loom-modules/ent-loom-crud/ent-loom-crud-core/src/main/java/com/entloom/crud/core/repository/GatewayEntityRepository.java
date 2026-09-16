package com.entloom.crud.core.repository;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.PageCountMode;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.api.model.PageRequest;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.capability.command.gateway.CommandGateway;
import com.entloom.crud.core.capability.command.spec.BatchCommand;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import com.entloom.crud.core.capability.query.gateway.QueryGateway;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionExecutor;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionPolicy;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 QueryGateway、CommandGateway 的默认实体仓储。
 *
 * @param <T> 实体类型
 * @param <ID> 主键类型
 */
final class GatewayEntityRepository<T, ID> implements EntityRepository<T, ID> {
    private final Class<T> entityType;
    private final Class<ID> idType;
    private final QueryGateway queryGateway;
    private final CommandGateway commandGateway;
    private final EntityMeta entityMeta;
    private final CrudWriteTransactionExecutor transactionExecutor;

    GatewayEntityRepository(
        Class<T> entityType,
        Class<ID> idType,
        QueryGateway queryGateway,
        CommandGateway commandGateway,
        EntityMetaRegistry entityMetaRegistry,
        CrudWriteTransactionExecutor transactionExecutor
    ) {
        this.entityType = Objects.requireNonNull(entityType, "entityType 不能为空");
        this.idType = Objects.requireNonNull(idType, "idType 不能为空");
        this.queryGateway = Objects.requireNonNull(queryGateway, "queryGateway 不能为空");
        this.commandGateway = Objects.requireNonNull(commandGateway, "commandGateway 不能为空");
        EntityMeta meta = Objects.requireNonNull(entityMetaRegistry, "entityMetaRegistry 不能为空").getEntityMeta(entityType);
        this.entityMeta = Objects.requireNonNull(meta, "实体元数据不能为空");
        if (meta.getIdField() == null || meta.getIdField().trim().isEmpty()) {
            throw new ValidationException("实体主键字段不能为空");
        }
        if (meta.resolveColumn(meta.getIdField()) == null) {
            throw new ValidationException("实体主键字段未映射列: " + meta.getIdField());
        }
        this.transactionExecutor = Objects.requireNonNull(transactionExecutor, "transactionExecutor 不能为空");
    }

    @Override
    public Optional<T> findById(ID id) {
        requireId(id);
        ID actualId = convertId(id);
        return Optional.ofNullable(queryGateway.findOne(querySpec(QueryOperation.FIND_ONE, idQuery(actualId))));
    }

    @Override
    public T getRequired(ID id) {
        requireId(id);
        ID actualId = convertId(id);
        return queryGateway.detail(querySpec(QueryOperation.DETAIL, idQuery(actualId)));
    }

    @Override
    public List<T> findAll(EntityQuery<T> query) {
        return queryGateway.list(querySpec(QueryOperation.LIST, actualQuery(query)));
    }

    @Override
    public PageResult<T> findPage(EntityQuery<T> query) {
        EntityQuery<T> actual = actualQuery(query);
        if (actual.getPage() == null) {
            actual = copyWithPage(actual, new PageRequest(1, 10));
        }
        return queryGateway.page(querySpec(QueryOperation.PAGE, actual));
    }

    @Override
    public boolean existsById(ID id) {
        return findById(id).isPresent();
    }

    @Override
    public long count(EntityQuery<T> query) {
        EntityQuery<T> actual = copyWithPage(actualQuery(query), new PageRequest(1, 1), PageCountMode.EXACT);
        return queryGateway.page(querySpec(QueryOperation.PAGE, actual)).getTotal();
    }

    @Override
    public ID insert(T entity) {
        requireEntity(entity);
        Object entityId = extractEntityId(entity);
        Object actualId = entityId == null ? null : convertId(entityId);
        Object result = commandGateway.action(commandSpec(
            CommandOperation.CREATE,
            new WriteCommand<Map<String, Object>>(CommandOperation.CREATE, actualId, entityValues(entity, true)),
            Collections.<QueryFilter>emptyList()
        ));
        return resultId(result, actualId);
    }

    @Override
    public int updateById(ID id, EntityPatch<T> patch) {
        requireId(id);
        ID actualId = convertId(id);
        return rows(commandGateway.action(commandSpec(
            CommandOperation.UPDATE,
            new WriteCommand<Map<String, Object>>(CommandOperation.UPDATE, actualId, patchValues(patch)),
            Collections.<QueryFilter>emptyList()
        )));
    }

    @Override
    public int deleteById(ID id) {
        requireId(id);
        ID actualId = convertId(id);
        return rows(commandGateway.action(commandSpec(
            CommandOperation.DELETE,
            new WriteCommand<Map<String, Object>>(CommandOperation.DELETE, actualId, Collections.<String, Object>emptyMap()),
            Collections.<QueryFilter>emptyList()
        )));
    }

    @Override
    public List<ID> insertBatch(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> actualEntities = new ArrayList<T>(entities);
        List<WriteCommand<Object>> items = new ArrayList<WriteCommand<Object>>();
        for (T entity : actualEntities) {
            requireEntity(entity);
            Object entityId = extractEntityId(entity);
            items.add(new WriteCommand<Object>(
                CommandOperation.CREATE,
                entityId == null ? null : convertId(entityId),
                entityValues(entity, true)
            ));
        }
        return transactionExecutor.execute(CrudWriteTransactionPolicy.SINGLE_TRANSACTION, () -> {
            Object result = commandGateway.action(commandSpec(
                CommandOperation.CREATE_BATCH,
                BatchCommand.of(items),
                Collections.<QueryFilter>emptyList()
            ));
            return batchIds(result, actualEntities);
        });
    }

    @Override
    public int updateBatch(List<EntityUpdate<T, ID>> updates) {
        if (updates == null || updates.isEmpty()) {
            return 0;
        }
        List<EntityUpdate<T, ID>> actualUpdates = new ArrayList<EntityUpdate<T, ID>>(updates);
        List<WriteCommand<Map<String, Object>>> items = new ArrayList<WriteCommand<Map<String, Object>>>();
        for (EntityUpdate<T, ID> update : actualUpdates) {
            if (update == null) {
                throw new ValidationException("批量更新项不能为空");
            }
            requireId(update.getId());
            ID actualId = convertId(update.getId());
            items.add(new WriteCommand<Map<String, Object>>(
                CommandOperation.UPDATE,
                actualId,
                patchValues(update.getPatch()),
                Collections.<QueryFilter>emptyList(),
                update.getExpectedVersion()
            ));
        }
        return transactionExecutor.execute(CrudWriteTransactionPolicy.SINGLE_TRANSACTION, () -> {
            return rows(commandGateway.action(commandSpec(
                CommandOperation.UPDATE_BATCH,
                BatchCommand.of(items),
                Collections.<QueryFilter>emptyList()
            )));
        });
    }

    @Override
    public int deleteBatchByIds(Collection<ID> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<ID> actualIds = new ArrayList<ID>(ids);
        List<WriteCommand<Map<String, Object>>> items = new ArrayList<WriteCommand<Map<String, Object>>>();
        for (ID id : actualIds) {
            requireId(id);
            ID actualId = convertId(id);
            items.add(new WriteCommand<Map<String, Object>>(
                CommandOperation.DELETE,
                actualId,
                Collections.<String, Object>emptyMap()
            ));
        }
        return transactionExecutor.execute(CrudWriteTransactionPolicy.SINGLE_TRANSACTION, () -> {
            return rows(commandGateway.action(commandSpec(
                CommandOperation.DELETE_BATCH,
                BatchCommand.of(items),
                Collections.<QueryFilter>emptyList()
            )));
        });
    }

    @Override
    public T save(T entity) {
        requireEntity(entity);
        Object rawId = extractEntityId(entity);
        ID id = rawId == null ? null : convertId(rawId);
        boolean existing = id != null && existsById(id);
        CommandOperation operation = existing ? CommandOperation.UPDATE : CommandOperation.CREATE;
        Object result = commandGateway.action(commandSpec(
            operation,
            new WriteCommand<Map<String, Object>>(
                operation,
                id,
                entityValues(entity, !existing)
            ),
            Collections.<QueryFilter>emptyList()
        ));
        return getRequired(resultId(result, id));
    }

    @Override
    public List<T> saveBatch(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> actualEntities = new ArrayList<T>(entities);
        List<ID> ids = new ArrayList<ID>();
        List<Map<String, Object>> createValues = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> updateValues = new ArrayList<Map<String, Object>>();
        for (T entity : actualEntities) {
            requireEntity(entity);
            Object rawId = extractEntityId(entity);
            ids.add(rawId == null ? null : convertId(rawId));
            createValues.add(entityValues(entity, true));
            updateValues.add(entityValues(entity, false));
        }
        return transactionExecutor.execute(CrudWriteTransactionPolicy.SINGLE_TRANSACTION, () -> {
            List<WriteCommand<Object>> items = new ArrayList<WriteCommand<Object>>();
            for (int index = 0; index < actualEntities.size(); index++) {
                ID id = ids.get(index);
                boolean existing = id != null && existsById(id);
                CommandOperation operation = existing
                    ? CommandOperation.UPDATE
                    : CommandOperation.CREATE;
                Map<String, Object> values = existing ? updateValues.get(index) : createValues.get(index);
                items.add(new WriteCommand<Object>(operation, id, values));
            }
            Object result = commandGateway.action(commandSpec(
                CommandOperation.SAVE_OR_UPDATE_BATCH,
                BatchCommand.of(items),
                Collections.<QueryFilter>emptyList()
            ));
            List<ID> resultIds = batchIds(result, actualEntities);
            List<T> saved = new ArrayList<T>();
            for (ID id : resultIds) {
                saved.add(getRequired(id));
            }
            return saved;
        });
    }

    @Override
    public int delete(EntityQuery<T> query) {
        List<QueryFilter> filters = requireConditionalFilters(query);
        return rows(commandGateway.action(commandSpec(
            CommandOperation.DELETE,
            new WriteCommand<Map<String, Object>>(
                CommandOperation.DELETE,
                null,
                Collections.<String, Object>emptyMap(),
                filters,
                null
            ),
            filters
        )));
    }

    @Override
    public int update(EntityQuery<T> query, EntityPatch<T> patch) {
        List<QueryFilter> filters = requireConditionalFilters(query);
        return rows(commandGateway.action(commandSpec(
            CommandOperation.UPDATE,
            new WriteCommand<Map<String, Object>>(
                CommandOperation.UPDATE,
                null,
                patchValues(patch),
                filters,
                null
            ),
            filters
        )));
    }

    private QuerySpec<T> querySpec(QueryOperation operation, EntityQuery<T> query) {
        return QuerySpec.<T>builder()
            .op(operation)
            .rootType(entityType)
            .entityClasses(Collections.<Class<?>>singletonList(entityType))
            .filters(query.getFilters())
            .sorts(query.getSorts())
            .page(query.getPage())
            .limit(query.getLimit())
            .countMode(query.getCountMode())
            .selectFields(query.getSelectFields())
            .expandRelations(query.getExpandRelations())
            .resultType(entityType)
            .build();
    }

    private CommandSpec<Object> commandSpec(CommandOperation operation, Object payload, List<QueryFilter> filters) {
        return CommandSpec.<Object>builder()
            .op(operation)
            .rootType(entityType)
            .entityClasses(Collections.<Class<?>>singletonList(entityType))
            .payload(payload)
            .targetFilters(filters)
            .resultType(Map.class)
            .build();
    }

    private EntityQuery<T> idQuery(ID id) {
        return EntityQuery.<T>builder().eq(entityMeta.getIdField(), id).build();
    }

    private EntityQuery<T> actualQuery(EntityQuery<T> query) {
        return query == null ? EntityQuery.<T>empty() : query;
    }

    private EntityQuery<T> copyWithPage(EntityQuery<T> source, PageRequest page) {
        return copyWithPage(source, page, source.getCountMode());
    }

    private EntityQuery<T> copyWithPage(EntityQuery<T> source, PageRequest page, PageCountMode countMode) {
        EntityQuery.Builder<T> builder = EntityQuery.<T>builder()
            .page(page.getPage(), page.getLimit())
            .countMode(countMode)
            .select(source.getSelectFields().toArray(new String[0]))
            .expand(source.getExpandRelations().toArray(new String[0]));
        if (source.getLimit() != null) {
            builder.limit(source.getLimit().intValue());
        }
        for (QueryFilter filter : source.getFilters()) {
            builder.filter(filter.getField(), filter.getOperator(), filter.getValue());
        }
        source.getSorts().forEach(sort -> builder.orderBy(sort.getField(), sort.getDirection()));
        return builder.build();
    }

    private List<QueryFilter> requireConditionalFilters(EntityQuery<T> query) {
        List<QueryFilter> filters = actualQuery(query).getFilters();
        if (filters.isEmpty()) {
            throw new ValidationException("条件更新或删除必须至少包含一个过滤条件");
        }
        for (QueryFilter filter : filters) {
            if (filter == null || filter.getOperator() != FilterOperator.EQ && filter.getOperator() != FilterOperator.IN) {
                throw new ValidationException("条件更新或删除仅支持 EQ、IN 过滤操作符");
            }
            if (filter.getOperator() == FilterOperator.IN
                && (!(filter.getValue() instanceof Collection<?>) || ((Collection<?>) filter.getValue()).isEmpty())) {
                throw new ValidationException("条件更新或删除的 IN 值必须是非空集合");
            }
        }
        return filters;
    }

    private Map<String, Object> patchValues(EntityPatch<T> patch) {
        if (patch == null || patch.getValues().isEmpty()) {
            throw new ValidationException("更新内容不能为空");
        }
        return new LinkedHashMap<String, Object>(patch.getValues());
    }

    /**
     * 将实体转换为默认 JDBC 写入链接受的字段映射。
     * 更新时排除主键、只读、不可变和数据范围字段；新增时保留数据范围字段供治理校验。
     */
    private Map<String, Object> entityValues(T entity, boolean forCreate) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        if (entity instanceof CrudRecord) {
            values.putAll(((CrudRecord) entity).asMap());
        } else if (entity instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) entity).entrySet()) {
                if (entry.getKey() != null) {
                    values.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        } else {
            for (Field field : fieldsOf(entity.getClass())) {
                try {
                    field.setAccessible(true);
                    values.put(field.getName(), field.get(entity));
                } catch (IllegalAccessException ex) {
                    throw new ValidationException("实体字段读取失败: " + field.getName());
                }
            }
        }
        values.remove(entityMeta.getIdField());
        values.remove("id");
        for (String field : new ArrayList<String>(values.keySet())) {
            com.entloom.crud.core.runtime.meta.EntityFieldMeta fieldMeta = entityMeta.resolveFieldMeta(field);
            if (fieldMeta != null && (fieldMeta.isImmutable()
                || (!forCreate && (fieldMeta.isScopeField() || !fieldMeta.isWritable())))) {
                values.remove(field);
            }
        }
        return values;
    }

    private List<Field> fieldsOf(Class<?> type) {
        List<Field> fields = new ArrayList<Field>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    && !java.lang.reflect.Modifier.isTransient(field.getModifiers())
                    && !field.isSynthetic()) {
                    fields.add(field);
                }
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private void requireId(ID id) {
        if (id == null || id instanceof String && ((String) id).trim().isEmpty()) {
            throw new ValidationException("实体主键不能为空");
        }
    }

    private void requireEntity(T entity) {
        if (entity == null) {
            throw new ValidationException("实体不能为空");
        }
    }

    private int rows(Object result) {
        Object value = resultValue(result, "rows");
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private ID resultId(Object result, Object fallback) {
        Object value = resultValue(result, "id");
        Object id = value == null ? fallback : value;
        if (id == null) {
            throw new ValidationException("写入结果未返回实体主键: " + entityMeta.getIdField());
        }
        return convertId(id);
    }

    private List<ID> batchIds(Object result, List<T> entities) {
        Object rawItems = resultValue(result, "items");
        if (!(rawItems instanceof List<?>)) {
            throw new ValidationException("批量写入结果未返回 items");
        }
        List<?> items = (List<?>) rawItems;
        if (items.size() != entities.size()) {
            throw new ValidationException("批量写入结果数量与请求数量不一致");
        }
        List<ID> ids = new ArrayList<ID>();
        for (int index = 0; index < items.size(); index++) {
            Object item = items.get(index);
            Object id = item instanceof Map<?, ?> ? ((Map<?, ?>) item).get("id") : null;
            if (id == null) {
                id = extractEntityId(entities.get(index));
            }
            if (id == null) {
                throw new ValidationException("批量写入结果未返回第 " + index + " 项主键");
            }
            ids.add(convertId(id));
        }
        return ids;
    }

    private Object resultValue(Object result, String key) {
        Object data = result instanceof CommandResult<?> ? ((CommandResult<?>) result).getData() : result;
        return data instanceof Map<?, ?> ? ((Map<?, ?>) data).get(key) : null;
    }

    private Object extractEntityId(T entity) {
        String idField = entityMeta.getIdField();
        if (entity instanceof CrudRecord) {
            CrudRecord record = (CrudRecord) entity;
            Object id = record.get(idField);
            return id == null && (idField == null || !"id".equals(idField)) ? record.get("id") : id;
        }
        if (entity instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) entity;
            Object id = map.get(idField);
            return id == null && (idField == null || !"id".equals(idField)) ? map.get("id") : id;
        }
        Field field = findField(entity.getClass(), idField);
        if (field == null) {
            throw new ValidationException("实体缺少主键字段: " + idField);
        }
        try {
            field.setAccessible(true);
            return field.get(entity);
        } catch (IllegalAccessException ex) {
            throw new ValidationException("实体主键读取失败: " + idField);
        }
    }

    private Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private ID convertId(Object value) {
        if (value == null) {
            return null;
        }
        if (idType.isInstance(value)) {
            return idType.cast(value);
        }
        String raw = String.valueOf(value).trim();
        try {
            if (idType == String.class) {
                return (ID) raw;
            }
            if (idType == Long.class || idType == Long.TYPE) {
                return (ID) Long.valueOf(integerValue(raw, "Long").longValueExact());
            }
            if (idType == Integer.class || idType == Integer.TYPE) {
                return (ID) Integer.valueOf(integerValue(raw, "Integer").intValueExact());
            }
            if (idType == Short.class || idType == Short.TYPE) {
                return (ID) Short.valueOf(integerValue(raw, "Short").shortValueExact());
            }
            if (idType == BigInteger.class) {
                return (ID) integerValue(raw, "BigInteger").toBigIntegerExact();
            }
            if (idType == BigDecimal.class) {
                return (ID) new BigDecimal(raw);
            }
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new ValidationException("实体主键无法转换为 " + idType.getName() + ": " + value);
        }
        throw new ValidationException("实体主键无法转换为 " + idType.getName() + ": " + value);
    }

    private BigDecimal integerValue(String raw, String targetType) {
        if (raw.isEmpty()) {
            throw new NumberFormatException("empty " + targetType);
        }
        return new BigDecimal(raw);
    }
}

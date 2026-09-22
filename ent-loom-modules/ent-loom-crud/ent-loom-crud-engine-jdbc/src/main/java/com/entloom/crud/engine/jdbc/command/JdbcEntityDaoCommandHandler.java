package com.entloom.crud.engine.jdbc.command;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.capability.command.handler.CrudCommandHandler;
import com.entloom.crud.core.capability.command.patch.DefaultCommandPayloadBinder;
import com.entloom.crud.core.capability.command.patch.UpdatePatch;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.command.spec.BatchCommand;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import com.entloom.crud.core.capability.dao.EntityAccessScope;
import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.core.capability.dao.EntityDaoFactory;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.capability.dao.RowConstraint;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.exception.EntityDaoWriteMissException;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionCallback;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionExecutor;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionPolicy;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.contract.CrudInputContract;
import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.validation.RequiredFieldValidator;
import com.entloom.crud.core.runtime.validation.CreateDefaultValueApplier;
import com.entloom.crud.engine.jdbc.dao.JdbcEntityDaoFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将显式主键实体的写命令统一切换到 EntityDao。
 *
 * <p>数据库生成主键实体在默认 Command 路由中仍交给回退处理器；业务直接调用 EntityDao 时已支持
 * 生成主键回填。显式主键实体的单条、批量和 save-or-update 命令不再调用旧的主键 SQL。</p>
 */
public final class JdbcEntityDaoCommandHandler<P, R> implements CrudCommandHandler<P, R> {
    private final EntityMetaRegistry metaRegistry;
    private final EntityDaoFactory daoFactory;
    private final CrudCommandHandler<P, R> fallback;
    private final DefaultCommandPayloadBinder payloadBinder;
    private final RequiredFieldValidator requiredFieldValidator;
    private final CrudInputContract inputContract;
    private final CreateDefaultValueApplier createDefaultValueApplier;
    private final CrudWriteTransactionExecutor transactionExecutor;

    public JdbcEntityDaoCommandHandler(
        EntityMetaRegistry metaRegistry,
        EntityDaoFactory daoFactory,
        CrudCommandHandler<P, R> fallback
    ) {
        this(metaRegistry, daoFactory, fallback, resolveTransactionExecutor(daoFactory));
    }

    public JdbcEntityDaoCommandHandler(
        EntityMetaRegistry metaRegistry,
        EntityDaoFactory daoFactory,
        CrudCommandHandler<P, R> fallback,
        CrudWriteTransactionExecutor transactionExecutor
    ) {
        this(metaRegistry, daoFactory, fallback, transactionExecutor, CrudInputContract.empty());
    }

    public JdbcEntityDaoCommandHandler(
        EntityMetaRegistry metaRegistry,
        EntityDaoFactory daoFactory,
        CrudCommandHandler<P, R> fallback,
        CrudWriteTransactionExecutor transactionExecutor,
        CrudInputContract inputContract
    ) {
        if (metaRegistry == null || daoFactory == null || fallback == null) {
            throw new ValidationException("DAO 命令处理器依赖不能为空");
        }
        this.metaRegistry = metaRegistry;
        this.daoFactory = daoFactory;
        this.fallback = fallback;
        this.payloadBinder = new DefaultCommandPayloadBinder();
        this.inputContract = inputContract == null ? CrudInputContract.empty() : inputContract;
        this.requiredFieldValidator = new RequiredFieldValidator(this.inputContract);
        this.createDefaultValueApplier = new CreateDefaultValueApplier();
        this.transactionExecutor = transactionExecutor == null
            ? resolveTransactionExecutor(daoFactory)
            : transactionExecutor;
    }

    @Override
    public boolean supports(CommandSpec<P> spec) {
        return spec != null && spec.getOp() != null;
    }

    @Override
    public R action(CommandSpec<P> spec) {
        if (spec == null || spec.getRootType() == null) {
            throw new ValidationException("DAO 命令根实体不能为空");
        }
        EntityMeta meta = metaRegistry.getEntityMeta(spec.getRootType());
        if (meta.getIdPolicy() != EntityIdPolicy.EXPLICIT) {
            return fallback.action(spec);
        }
        switch (spec.getOp()) {
            case CREATE:
                return create(spec);
            case UPDATE:
                return update(spec);
            case DELETE:
                return delete(spec);
            case SAVE_OR_UPDATE:
                return saveOrUpdate(spec);
            case CREATE_BATCH:
            case UPDATE_BATCH:
            case DELETE_BATCH:
            case SAVE_OR_UPDATE_BATCH:
                return batch(spec);
            default:
                return fallback.action(spec);
        }
    }

    @Override
    public R create(CommandSpec<P> spec) {
        rejectVersion(spec);
        EntityMeta meta = metaRegistry.getEntityMeta(spec.getRootType());
        Map<String, Object> values = payloadValues(spec, meta);
        Object id = resolveId(values, meta);
        requiredFieldValidator.validateCreateValues(values, meta, id);
        createDefaultValueApplier.applyToValues(values, meta);
        values.put(meta.getIdField(), id);
        Object entity = payloadBinder.bindEntity(values, spec.getRootType(), meta);
        Object insertedId = dao(meta, scope(spec)).insert(entity);
        return result(spec, 1, insertedId);
    }

    @Override
    public R update(CommandSpec<P> spec) {
        EntityMeta meta = metaRegistry.getEntityMeta(spec.getRootType());
        Map<String, Object> values = payloadValues(spec, meta);
        validateUpdateContract(values, meta);
        applyExpectedVersion(values, spec, meta);
        Object id = resolveId(values, meta);
        values.put(meta.getIdField(), id);
        UpdatePatch<Object> patch = payloadBinder.bindUpdatePatch(values, entityClass(spec), meta);
        int rows = dao(meta, scope(spec)).updateById(id, patch);
        return result(spec, rows, id);
    }

    @Override
    public R delete(CommandSpec<P> spec) {
        EntityMeta meta = metaRegistry.getEntityMeta(spec.getRootType());
        Map<String, Object> values = payloadValues(spec, meta);
        Object id = resolveId(values, meta);
        EntityDao<Object, Object> entityDao = dao(meta, scope(spec));
        int rows = spec.getExpectedVersion() == null
            ? entityDao.deleteById(id)
            : entityDao.deleteById(id, spec.getExpectedVersion().longValue());
        if (rows == 0) {
            throw new EntityDaoWriteMissException("目标不存在或不可写: " + meta.getEntityName());
        }
        return result(spec, rows, id);
    }

    @Override
    public R batch(CommandSpec<P> spec) {
        if (!(spec.getPayload() instanceof BatchCommand<?>)) {
            throw new ValidationException("批量载荷必须是 BatchCommand");
        }
        List<? extends WriteCommand<?>> items = ((BatchCommand<?>) spec.getPayload()).getItems();
        if (items == null || items.isEmpty()) {
            throw new ValidationException("批量命令 items 不能为空");
        }
        if (items.size() > JdbcEntityDaoFactory.DEFAULT_MAX_BATCH_SIZE) {
            throw new ValidationException(
                "批量命令数量超过上限 " + JdbcEntityDaoFactory.DEFAULT_MAX_BATCH_SIZE
            );
        }
        if (transactionExecutor == null) {
            throw new ValidationException(
                "JDBC DAO 批量命令必须配置 CrudWriteTransactionExecutor，避免部分提交"
            );
        }
        return transactionExecutor.execute(
            CrudWriteTransactionPolicy.SINGLE_TRANSACTION,
            new CrudWriteTransactionCallback<R>() {
                @Override
                public R execute() {
                    return executeBatchItems(spec, items);
                }
            }
        );
    }

    private R executeBatchItems(CommandSpec<P> spec, List<? extends WriteCommand<?>> items) {
        int rows = 0;
        List<Map<String, Object>> itemResults = new ArrayList<Map<String, Object>>();
        for (int index = 0; index < items.size(); index++) {
            WriteCommand<?> item = items.get(index);
            if (item == null) {
                throw new ValidationException("批量命令 item 不能为空");
            }
            CommandOperation operation = batchChildOperation(spec.getOp(), item.getOp());
            R itemResult = action(childSpec(spec, item, operation));
            int itemRows = rows(itemResult);
            rows += itemRows;
            itemResults.add(batchItemResult(index, operation, item, itemResult, itemRows));
        }
        return batchResult(spec, rows, itemResults);
    }

    private static CrudWriteTransactionExecutor resolveTransactionExecutor(EntityDaoFactory daoFactory) {
        if (daoFactory instanceof JdbcEntityDaoFactory) {
            return ((JdbcEntityDaoFactory) daoFactory).getTransactionExecutor();
        }
        return null;
    }

    private R saveOrUpdate(CommandSpec<P> spec) {
        EntityMeta meta = metaRegistry.getEntityMeta(spec.getRootType());
        Map<String, Object> values = payloadValues(spec, meta);
        Object id = resolveId(values, meta);
        EntityDao<Object, Object> entityDao = dao(meta, scope(spec));
        CommandOperation operation = entityDao.findById(id).isPresent()
            ? CommandOperation.UPDATE
            : CommandOperation.CREATE;
        R result = action(childSpec(spec, new WriteCommand<Object>(operation, id, values), operation));
        return saveOrUpdateResult(spec, operation, id, rows(result));
    }

    private CommandOperation batchChildOperation(CommandOperation batchOperation, CommandOperation itemOperation) {
        if (itemOperation != null) {
            if (itemOperation == CommandOperation.ACTION || isBatchOperation(itemOperation)) {
                throw new ValidationException("批量命令不支持子操作: " + itemOperation);
            }
            return itemOperation;
        }
        switch (batchOperation) {
            case CREATE_BATCH:
                return CommandOperation.CREATE;
            case UPDATE_BATCH:
                return CommandOperation.UPDATE;
            case DELETE_BATCH:
                return CommandOperation.DELETE;
            case SAVE_OR_UPDATE_BATCH:
                return CommandOperation.SAVE_OR_UPDATE;
            default:
                throw new ValidationException("不支持的批量命令操作: " + batchOperation);
        }
    }

    private boolean isBatchOperation(CommandOperation operation) {
        return operation == CommandOperation.CREATE_BATCH
            || operation == CommandOperation.UPDATE_BATCH
            || operation == CommandOperation.DELETE_BATCH
            || operation == CommandOperation.SAVE_OR_UPDATE_BATCH;
    }

    @SuppressWarnings("unchecked")
    private CommandSpec<P> childSpec(CommandSpec<P> source, WriteCommand<?> item, CommandOperation operation) {
        return (CommandSpec<P>) CommandSpec.builder()
            .scene(source.getScene())
            .rootType(source.getRootType())
            .entityClasses(source.getEntityClasses())
            .subject(source.getSubject())
            .attributes(source.getAttributes())
            .grantedScope(source.getGrantedScope())
            .governanceScope(source.getGovernanceScope())
            .accessDecision(source.getAccessDecision())
            .resultType(source.getResultType())
            .op(operation)
            .payload(item)
            .expectedVersion(item.getExpectedVersion() == null ? source.getExpectedVersion() : item.getExpectedVersion())
            .targetFilters(item.getTargetFilters())
            .build();
    }

    private int rows(Object result) {
        Object data = result;
        if (result instanceof CommandResult<?>) {
            data = ((CommandResult<?>) result).getData();
        }
        if (!(data instanceof Map<?, ?>)) {
            return 0;
        }
        Object value = ((Map<?, ?>) data).get("rows");
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private Map<String, Object> batchItemResult(
        int index,
        CommandOperation operation,
        WriteCommand<?> item,
        Object result,
        int rows
    ) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("index", Integer.valueOf(index));
        value.put("rows", Integer.valueOf(rows));
        if (item.getId() != null) {
            value.put("id", item.getId());
        }
        Object data = result instanceof CommandResult<?> ? ((CommandResult<?>) result).getData() : result;
        Object actualOperation = data instanceof Map<?, ?> ? ((Map<?, ?>) data).get("operation") : null;
        value.put("operation", actualOperation == null ? operation.name() : actualOperation);
        if (data instanceof Map<?, ?> && ((Map<?, ?>) data).get("id") != null) {
            value.put("id", ((Map<?, ?>) data).get("id"));
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private R batchResult(CommandSpec<P> spec, int rows, List<Map<String, Object>> items) {
        if (spec.getResultType() == Void.class) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("rows", Integer.valueOf(rows));
        data.put("items", items);
        return CommandResult.class.isAssignableFrom(spec.getResultType())
            ? (R) CommandResult.success(data)
            : (R) data;
    }

    @SuppressWarnings("unchecked")
    private R saveOrUpdateResult(CommandSpec<P> spec, CommandOperation operation, Object id, int rows) {
        if (spec.getResultType() == Void.class) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("operation", operation.name());
        data.put("rows", Integer.valueOf(rows));
        data.put("id", id);
        return CommandResult.class.isAssignableFrom(spec.getResultType())
            ? (R) CommandResult.success(data)
            : (R) data;
    }

    @SuppressWarnings("unchecked")
    private Class<Object> entityClass(CommandSpec<P> spec) {
        return (Class<Object>) spec.getRootType();
    }

    @SuppressWarnings("unchecked")
    private EntityDao<Object, Object> dao(EntityMeta meta, EntityAccessScope scope) {
        Class<Object> entityClass = (Class<Object>) meta.getEntityType();
        Class<Object> idClass = (Class<Object>) wrap(meta.resolveFieldMeta(meta.getIdField()).getJavaType());
        EntityType<Object, Object> entityType = EntityType.of(entityClass, idClass);
        return (EntityDao<Object, Object>) (EntityDao<?, ?>) daoFactory.scoped(entityType, scope);
    }

    private Map<String, Object> payloadValues(CommandSpec<P> spec, EntityMeta meta) {
        Object payload = spec.getPayload();
        Object rawValues = payload;
        Object commandId = null;
        List<QueryFilter> targetFilters = spec.getTargetFilters();
        if (payload instanceof WriteCommand<?>) {
            WriteCommand<?> command = (WriteCommand<?>) payload;
            rawValues = command.getValues();
            commandId = command.getId();
            if (command.getTargetFilters() != null && !command.getTargetFilters().isEmpty()) {
                targetFilters = command.getTargetFilters();
            }
        }
        Map<String, Object> values = new LinkedHashMap<String, Object>(
            payloadBinder.bindFieldMap(rawValues, meta)
        );
        if (commandId != null) {
            Object payloadId = values.get(meta.getIdField());
            if (payloadId != null && !sameValue(commandId, payloadId)) {
                throw new ValidationException("WriteCommand.id 与 payload 主键字段不一致: " + meta.getIdField());
            }
            values.put(meta.getIdField(), commandId);
        }
        applyTargetId(values, meta, targetFilters);
        return values;
    }

    private void applyTargetId(Map<String, Object> values, EntityMeta meta, List<QueryFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return;
        }
        for (QueryFilter filter : filters) {
            if (filter == null || !meta.getIdField().equals(filter.getField())
                || filter.getOperator() != FilterOperator.EQ || filter.getValue() == null) {
                throw new ValidationException("DAO 主键路径只支持主键等值目标条件");
            }
            Object current = values.get(meta.getIdField());
            if (current != null && !sameValue(current, filter.getValue())) {
                throw new ValidationException("目标条件主键与载荷主键不一致: " + meta.getIdField());
            }
            values.put(meta.getIdField(), filter.getValue());
        }
    }

    private Object resolveId(Map<String, Object> values, EntityMeta meta) {
        Object id = values.get(meta.getIdField());
        if (id == null) {
            throw new ValidationException("DAO 主键路径要求载荷包含 payload.id: " + meta.getIdField());
        }
        return id;
    }

    private EntityAccessScope scope(CommandSpec<P> spec) {
        return EntityAccessScope.of(toRowConstraint(spec.getGovernanceScope()));
    }

    private RowConstraint toRowConstraint(CrudDataScope dataScope) {
        if (dataScope == null || dataScope.isExplicitAll()) {
            return RowConstraint.unrestricted();
        }
        List<RowConstraint> constraints = new ArrayList<RowConstraint>();
        for (Map.Entry<String, Object> entry : dataScope.getDimensions().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Collection<?>) {
                constraints.add(RowConstraint.in(entry.getKey(), (Collection<?>) value));
            } else {
                constraints.add(RowConstraint.eq(entry.getKey(), value));
            }
        }
        return RowConstraint.and(constraints.toArray(new RowConstraint[constraints.size()]));
    }

    private void rejectVersion(CommandSpec<P> spec) {
        if (spec.getExpectedVersion() != null) {
            throw new ValidationException("DAO 首期不支持 expectedVersion");
        }
    }

    private void validateUpdateContract(Map<String, Object> values, EntityMeta meta) {
        try {
            inputContract.validateUpdate(values, meta);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(ex.getMessage());
        }
    }

    private void applyExpectedVersion(Map<String, Object> values, CommandSpec<P> spec, EntityMeta meta) {
        Long expectedVersion = spec.getExpectedVersion();
        if (expectedVersion == null) {
            return;
        }
        Object payloadVersion = values.get("version");
        if (payloadVersion != null && !sameValue(payloadVersion, expectedVersion)) {
            throw new ValidationException("命令 expectedVersion 与载荷 version 不一致");
        }
        if (!meta.getAllowedFields().contains("version")) {
            throw new ValidationException("实体未启用 version，不能携带 expectedVersion: " + meta.getEntityName());
        }
        values.put("version", expectedVersion);
    }

    private boolean sameValue(Object left, Object right) {
        if (left == null || right == null) {
            return left == right;
        }
        if (left instanceof Number && right instanceof Number) {
            return new java.math.BigDecimal(String.valueOf(left))
                .compareTo(new java.math.BigDecimal(String.valueOf(right))) == 0;
        }
        return String.valueOf(left).equals(String.valueOf(right));
    }

    @SuppressWarnings("unchecked")
    private R result(CommandSpec<P> spec, int rows, Object id) {
        if (spec.getResultType() == Void.class) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("rows", rows);
        if (id != null) {
            data.put("id", id);
        }
        if (CommandResult.class.isAssignableFrom(spec.getResultType())) {
            return (R) CommandResult.success(data);
        }
        if (Number.class.isAssignableFrom(spec.getResultType())) {
            return (R) Integer.valueOf(rows);
        }
        return (R) data;
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

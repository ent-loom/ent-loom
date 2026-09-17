package com.entloom.crud.engine.jdbc.command;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.capability.command.handler.CrudCommandHandler;
import com.entloom.crud.core.capability.command.patch.DefaultCommandPayloadBinder;
import com.entloom.crud.core.capability.command.patch.UpdatePatch;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import com.entloom.crud.core.capability.dao.EntityAccessScope;
import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.capability.dao.RowConstraint;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.engine.jdbc.dao.JdbcEntityDaoFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将选定实体的单条主键 CRUD 命令切换到 EntityDao。
 *
 * <p>该处理器保留旧处理器作为其它操作的委托，便于按真实执行链逐实体切换；DAO 路径本身不再
 * 调用旧的主键 SQL。</p>
 */
public final class JdbcEntityDaoCommandHandler<P, R> implements CrudCommandHandler<P, R> {
    private final EntityMetaRegistry metaRegistry;
    private final JdbcEntityDaoFactory daoFactory;
    private final CrudCommandHandler<P, R> fallback;
    private final DefaultCommandPayloadBinder payloadBinder;

    public JdbcEntityDaoCommandHandler(
        EntityMetaRegistry metaRegistry,
        JdbcEntityDaoFactory daoFactory,
        CrudCommandHandler<P, R> fallback
    ) {
        if (metaRegistry == null || daoFactory == null || fallback == null) {
            throw new ValidationException("DAO 命令处理器依赖不能为空");
        }
        this.metaRegistry = metaRegistry;
        this.daoFactory = daoFactory;
        this.fallback = fallback;
        this.payloadBinder = new DefaultCommandPayloadBinder();
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
        switch (spec.getOp()) {
            case CREATE:
                return create(spec);
            case UPDATE:
                return update(spec);
            case DELETE:
                return delete(spec);
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
        values.put(meta.getIdField(), id);
        Object entity = payloadBinder.bindEntity(values, spec.getRootType(), meta);
        Object insertedId = dao(meta, scope(spec)).insert(entity);
        return result(spec, 1, insertedId);
    }

    @Override
    public R update(CommandSpec<P> spec) {
        rejectVersion(spec);
        EntityMeta meta = metaRegistry.getEntityMeta(spec.getRootType());
        Map<String, Object> values = payloadValues(spec, meta);
        Object id = resolveId(values, meta);
        values.put(meta.getIdField(), id);
        UpdatePatch<Object> patch = payloadBinder.bindUpdatePatch(values, entityClass(spec), meta);
        int rows = dao(meta, scope(spec)).updateById(id, patch);
        return result(spec, rows, id);
    }

    @Override
    public R delete(CommandSpec<P> spec) {
        rejectVersion(spec);
        EntityMeta meta = metaRegistry.getEntityMeta(spec.getRootType());
        Map<String, Object> values = payloadValues(spec, meta);
        Object id = resolveId(values, meta);
        int rows = dao(meta, scope(spec)).deleteById(id);
        return result(spec, rows, id);
    }

    @Override
    public R batch(CommandSpec<P> spec) {
        return fallback.batch(spec);
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

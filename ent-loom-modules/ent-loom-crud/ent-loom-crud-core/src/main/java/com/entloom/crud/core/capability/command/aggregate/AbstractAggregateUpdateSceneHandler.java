package com.entloom.crud.core.capability.command.aggregate;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.core.capability.command.patch.CommandPayloadBinder;
import com.entloom.crud.core.capability.command.patch.DefaultCommandPayloadBinder;
import com.entloom.crud.core.capability.command.scene.AbstractEntityCommandSupport;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.command.spec.WriteCommand;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 聚合根 UPDATE 场景模板。
 *
 * @param <R> 聚合根实体类型
 */
public abstract class AbstractAggregateUpdateSceneHandler<R>
    extends AbstractEntityCommandSupport<R> {
    private static final String DEFAULT_ID_FIELD = "id";
    private static final String UPDATE_TIME_FIELD = "updateTime";

    private volatile Set<CrudRouteKey> routeKeys;

    protected AbstractAggregateUpdateSceneHandler(EntityMetaRegistry entityMetaRegistry) {
        this(entityMetaRegistry, new DefaultCommandPayloadBinder());
    }

    protected AbstractAggregateUpdateSceneHandler(EntityMetaRegistry entityMetaRegistry, CommandPayloadBinder payloadBinder) {
        super(entityMetaRegistry, payloadBinder);
    }

    @Override
    public final CommandOperation operation() {
        return CommandOperation.UPDATE;
    }

    protected abstract AggregateUpdateSpec<R> spec();

    @Override
    public Set<CrudRouteKey> routeKeys() {
        Set<CrudRouteKey> cached = routeKeys;
        if (cached == null) {
            AggregateUpdateSpec<R> aggregateSpec = spec();
            cached = aggregateSpec.routeKeys(resolveRelationEdges(aggregateSpec));
            routeKeys = cached;
        }
        return cached;
    }

    @Override
    public Object handle(CommandSpec<Object> commandSpec, SceneDelegate<CommandSpec<Object>, Object> delegateCall) {
        AggregateUpdateSpec<R> aggregateSpec = spec();
        EntityMeta rootMeta = getEntityMetaRegistry().getEntityMeta(aggregateSpec.getRootType());
        Map<String, Object> payload = toPayloadMap(commandSpec.getPayload());
        Object id = extractId(rootMeta, payload);
        if (id == null) {
            throw new ValidationException("aggregate update 需要合法 id");
        }

        Set<String> relationFields = relationFields(aggregateSpec);
        Map<String, RelationEdge> edgesByField = resolveRelationEdgesByField(aggregateSpec);
        Map<String, Object> delegatePayload = buildDelegatePayload(rootMeta, payload, id, relationFields);
        EntityPatch<R> rootPatch = new EntityPatch<R>(
            aggregateSpec.getRootType(),
            getPayloadBinder().bindEntity(payload, aggregateSpec.getRootType(), rootMeta),
            id,
            buildPresentFields(rootMeta, payload, relationFields),
            buildValuesForDelegate(rootMeta, delegatePayload)
        );
        List<AggregateRelationPatch<?>> relationPatches = buildRelationPatches(aggregateSpec, payload, edgesByField);

        beforeUpdate(rootPatch, relationPatches);
        boolean hasMainUpdates = hasMainUpdates(delegatePayload, rootMeta);
        boolean hasRelationUpdates = hasPresentRelation(relationPatches);
        Object result = null;
        if (hasMainUpdates) {
            result = delegateCall.invoke(toWriteSpec(commandSpec, rootMeta, id, delegatePayload));
        } else if (hasRelationUpdates) {
            Map<String, Object> touchPayload = buildTouchPayload(rootMeta, id);
            if (touchPayload.size() > 1) {
                result = delegateCall.invoke(toWriteSpec(commandSpec, rootMeta, id, touchPayload));
            }
        }

        for (AggregateRelationPatch<?> relationPatch : relationPatches) {
            if (relationPatch.isPresent()) {
                syncRelation(rootPatch, relationPatch);
            }
        }
        afterUpdate(rootPatch, relationPatches, result);
        return result == null ? CommandResult.success(rowsResult(0)) : result;
    }

    protected void beforeUpdate(EntityPatch<R> rootPatch, List<AggregateRelationPatch<?>> relationPatches) {
    }

    protected void afterUpdate(
        EntityPatch<R> rootPatch,
        List<AggregateRelationPatch<?>> relationPatches,
        Object result
    ) {
    }

    protected void syncRelation(EntityPatch<R> rootPatch, AggregateRelationPatch<?> relationPatch) {
        throw new ValidationException("未实现关系写入: " + relationPatch.getRelationField());
    }

    private List<RelationEdge> resolveRelationEdges(AggregateUpdateSpec<R> aggregateSpec) {
        return new ArrayList<RelationEdge>(resolveRelationEdgesByField(aggregateSpec).values());
    }

    private Map<String, RelationEdge> resolveRelationEdgesByField(AggregateUpdateSpec<R> aggregateSpec) {
        Map<String, RelationEdge> result = new LinkedHashMap<String, RelationEdge>();
        List<RelationEdge> outgoing = getEntityMetaRegistry().getRelationGraph(aggregateSpec.getRootType())
            .outgoingOf(aggregateSpec.getRootType());
        for (AggregateRelationSpec relationSpec : aggregateSpec.getRelationSpecs()) {
            RelationEdge matched = null;
            for (RelationEdge edge : outgoing) {
                if (edge != null && relationSpec.getRelationField().equals(edge.getRelationField())) {
                    matched = edge;
                    break;
                }
            }
            if (matched == null) {
                throw new ValidationException(
                    "未找到关系元数据: " + aggregateSpec.getRootType().getName() + "." + relationSpec.getRelationField()
                );
            }
            result.put(relationSpec.getRelationField(), matched);
        }
        return result;
    }

    private Set<String> relationFields(AggregateUpdateSpec<R> aggregateSpec) {
        Set<String> fields = new LinkedHashSet<String>();
        for (AggregateRelationSpec relationSpec : aggregateSpec.getRelationSpecs()) {
            fields.add(relationSpec.getRelationField());
        }
        return fields;
    }

    private Map<String, Object> buildDelegatePayload(
        EntityMeta rootMeta,
        Map<String, Object> payload,
        Object id,
        Set<String> relationFields
    ) {
        Map<String, Object> sanitized = new LinkedHashMap<String, Object>();
        sanitized.put(rootMeta.getIdField(), id);
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String field = entry.getKey();
            if (field == null || relationFields.contains(field) || isIdField(rootMeta, field)) {
                continue;
            }
            if (rootMeta.getAllowedFields().contains(field)) {
                sanitized.put(field, entry.getValue());
            }
        }
        return sanitized;
    }

    private boolean hasMainUpdates(Map<String, Object> delegatePayload, EntityMeta rootMeta) {
        for (String field : delegatePayload.keySet()) {
            if (!isIdField(rootMeta, field)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> buildPresentFields(EntityMeta rootMeta, Map<String, Object> payload, Set<String> relationFields) {
        Set<String> presentFields = new LinkedHashSet<String>();
        for (String field : payload.keySet()) {
            if (field == null) {
                continue;
            }
            if (rootMeta.getAllowedFields().contains(field) || isIdField(rootMeta, field) || relationFields.contains(field)) {
                presentFields.add(field);
            }
        }
        return presentFields;
    }

    private Map<String, Object> buildValuesForDelegate(EntityMeta rootMeta, Map<String, Object> delegatePayload) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : delegatePayload.entrySet()) {
            if (!isIdField(rootMeta, entry.getKey())) {
                values.put(entry.getKey(), entry.getValue());
            }
        }
        return values;
    }

    private CommandSpec<Object> toWriteSpec(
        CommandSpec<Object> source,
        EntityMeta rootMeta,
        Object id,
        Map<String, Object> payload
    ) {
        WriteCommand<Map<String, Object>> writeCommand = new WriteCommand<Map<String, Object>>(
            CommandOperation.UPDATE,
            id,
            buildValuesForDelegate(rootMeta, payload),
            source.getTargetFilters(),
            source.getExpectedVersion()
        );
        return source.toBuilder().payload(writeCommand).build();
    }

    private boolean hasPresentRelation(List<AggregateRelationPatch<?>> relationPatches) {
        for (AggregateRelationPatch<?> relationPatch : relationPatches) {
            if (relationPatch.isPresent()) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> buildTouchPayload(EntityMeta rootMeta, Object id) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put(rootMeta.getIdField(), id);
        if (rootMeta.getAllowedFields().contains(UPDATE_TIME_FIELD)) {
            payload.put(UPDATE_TIME_FIELD, new Date());
        }
        return payload;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<AggregateRelationPatch<?>> buildRelationPatches(
        AggregateUpdateSpec<R> aggregateSpec,
        Map<String, Object> payload,
        Map<String, RelationEdge> edgesByField
    ) {
        List<AggregateRelationPatch<?>> patches = new ArrayList<AggregateRelationPatch<?>>();
        for (AggregateRelationSpec relationSpec : aggregateSpec.getRelationSpecs()) {
            RelationEdge edge = edgesByField.get(relationSpec.getRelationField());
            Class childType = edge.getToEntity();
            boolean present = payload.containsKey(relationSpec.getRelationField());
            List items = present
                ? convertToEntityList(
                    payload.get(relationSpec.getRelationField()),
                    childType,
                    getEntityMetaRegistry().getEntityMeta(childType)
                )
                : Collections.emptyList();
            patches.add(new AggregateRelationPatch(relationSpec, edge, childType, present, items));
        }
        return patches;
    }

    private Object extractId(EntityMeta rootMeta, Map<String, Object> payload) {
        if (rootMeta.getIdField() != null && payload.containsKey(rootMeta.getIdField())) {
            return normalizeId(payload.get(rootMeta.getIdField()));
        }
        if (!DEFAULT_ID_FIELD.equals(rootMeta.getIdField()) && payload.containsKey(DEFAULT_ID_FIELD)) {
            return normalizeId(payload.get(DEFAULT_ID_FIELD));
        }
        return null;
    }

    private Object normalizeId(Object id) {
        if (id instanceof String && ((String) id).trim().isEmpty()) {
            return null;
        }
        return id;
    }

    private boolean isIdField(EntityMeta meta, String field) {
        return field != null
            && (field.equals(meta.getIdField()) || (!DEFAULT_ID_FIELD.equals(meta.getIdField()) && DEFAULT_ID_FIELD.equals(field)));
    }

    private Map<String, Object> rowsResult(int rows) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("rows", rows);
        return data;
    }

    private Map<String, Object> toPayloadMap(Object payload) {
        return getPayloadBinder().bindFieldMap(payload, null);
    }

    private <E> List<E> convertToEntityList(Object raw, Class<E> entityType, EntityMeta meta) {
        if (raw == null) {
            return Collections.emptyList();
        }
        Collection<?> source;
        if (raw instanceof Collection<?>) {
            source = (Collection<?>) raw;
        } else {
            source = Collections.singleton(raw);
        }
        List<E> result = new ArrayList<E>();
        for (Object item : source) {
            if (item == null) {
                continue;
            }
            result.add(getPayloadBinder().bindEntity(item, entityType, meta));
        }
        return result;
    }
}

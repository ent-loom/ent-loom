package com.entloom.crud.core.capability.command.scene;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.capability.command.patch.CommandPayloadBinder;
import com.entloom.crud.core.capability.command.patch.DefaultCommandPayloadBinder;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.command.spec.DeleteTarget;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 普通 DELETE 场景的目标定位模板基类。
 *
 * @param <T> 实体类型
 * @param <R> 业务返回类型
 */
public abstract class AbstractTargetDeleteHandler<T, R>
    extends AbstractEntityCommandSupport<T> {

    protected AbstractTargetDeleteHandler() {
        super();
    }

    protected AbstractTargetDeleteHandler(EntityMetaRegistry entityMetaRegistry, Class<T> entityType, String scene) {
        this(entityMetaRegistry, new DefaultCommandPayloadBinder(), entityType, scene);
    }

    protected AbstractTargetDeleteHandler(
        EntityMetaRegistry entityMetaRegistry,
        CommandPayloadBinder payloadBinder,
        Class<T> entityType,
        String scene
    ) {
        super(entityMetaRegistry, payloadBinder, entityType, scene);
    }

    @Override
    public final CommandOperation operation() {
        return CommandOperation.DELETE;
    }

    @Override
    public Object handle(CommandSpec<Object> spec, SceneDelegate<CommandSpec<Object>, Object> delegate) {
        DeleteTarget target = toDeleteTarget(spec);
        if (target.getId() == null && !hasFilters(target.getTargetFilters())) {
            throw new ValidationException("delete 需要合法 id 或 targetFilters");
        }
        return handleDelete(toTargetSpec(spec, target), delegate);
    }

    @SuppressWarnings("unchecked")
    protected R handleDelete(
        CommandSpec<DeleteTarget> spec,
        SceneDelegate<CommandSpec<Object>, Object> delegate
    ) {
        return (R) invokeDelegateDelete(spec, delegate);
    }

    protected Object invokeDelegateDelete(
        CommandSpec<DeleteTarget> spec,
        SceneDelegate<CommandSpec<Object>, Object> delegate
    ) {
        DeleteTarget target = spec.getPayload();
        WriteCommand<Map<String, Object>> writeCommand = new WriteCommand<Map<String, Object>>(
            CommandOperation.DELETE,
            target.getId(),
            Collections.<String, Object>emptyMap(),
            target.getTargetFilters(),
            target.getExpectedVersion()
        );
        return delegate.invoke(toRawSpec(spec, writeCommand));
    }

    private DeleteTarget toDeleteTarget(CommandSpec<Object> spec) {
        EntityMeta meta = entityMeta();
        Object payload = spec.getPayload();
        if (payload instanceof WriteCommand<?>) {
            WriteCommand<?> command = (WriteCommand<?>) payload;
            Object id = normalizeId(command.getId());
            if (id == null) {
                id = extractId(meta, command.getValues());
            }
            List<QueryFilter> filters = hasFilters(command.getTargetFilters())
                ? command.getTargetFilters()
                : spec.getTargetFilters();
            Long expectedVersion = command.getExpectedVersion() == null
                ? spec.getExpectedVersion()
                : command.getExpectedVersion();
            return new DeleteTarget(id, filters, expectedVersion);
        }
        Object id = extractId(meta, payload);
        return new DeleteTarget(id, spec.getTargetFilters(), spec.getExpectedVersion());
    }

    private Object extractId(EntityMeta meta, Object payload) {
        Map<String, Object> values = requirePayloadBinder().bindFieldMap(payload, null);
        Object id = null;
        String idField = meta.getIdField();
        if (idField != null && values.containsKey(idField)) {
            id = values.get(idField);
        } else if ((idField == null || !"id".equals(idField)) && values.containsKey("id")) {
            id = values.get("id");
        }
        return normalizeId(id);
    }

    private Object normalizeId(Object id) {
        if (id instanceof String && ((String) id).trim().isEmpty()) {
            return null;
        }
        return id;
    }

    private CommandSpec<DeleteTarget> toTargetSpec(CommandSpec<Object> source, DeleteTarget target) {
        return CommandSpec.<DeleteTarget>builder()
            .scene(source.getScene())
            .rootType(source.getRootType())
            .entityClasses(source.getEntityClasses())
            .subject(source.getSubject())
            .attributes(source.getAttributes())
            .grantedScope(source.getGrantedScope())
            .governanceScope(source.getGovernanceScope())
            .accessDecision(source.getAccessDecision())
            .op(source.getOp())
            .payload(target)
            .idempotencyKey(source.getIdempotencyKey())
            .expectedVersion(target.getExpectedVersion())
            .dryRun(source.isDryRun())
            .targetFilters(target.getTargetFilters())
            .resultType(source.getResultType())
            .build();
    }

    private CommandSpec<Object> toRawSpec(CommandSpec<DeleteTarget> source, Object payload) {
        return CommandSpec.<Object>builder()
            .scene(source.getScene())
            .rootType(source.getRootType())
            .entityClasses(source.getEntityClasses())
            .subject(source.getSubject())
            .attributes(source.getAttributes())
            .grantedScope(source.getGrantedScope())
            .governanceScope(source.getGovernanceScope())
            .accessDecision(source.getAccessDecision())
            .op(source.getOp())
            .payload(payload)
            .idempotencyKey(source.getIdempotencyKey())
            .expectedVersion(source.getExpectedVersion())
            .dryRun(source.isDryRun())
            .targetFilters(source.getTargetFilters())
            .resultType(source.getResultType())
            .build();
    }

    private boolean hasFilters(List<QueryFilter> filters) {
        return filters != null && !filters.isEmpty();
    }
}

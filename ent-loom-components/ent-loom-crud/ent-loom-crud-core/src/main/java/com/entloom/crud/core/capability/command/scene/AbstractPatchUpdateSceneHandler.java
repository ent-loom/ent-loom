package com.entloom.crud.core.capability.command.scene;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.capability.command.patch.CommandPayloadBinder;
import com.entloom.crud.core.capability.command.patch.DefaultCommandPayloadBinder;
import com.entloom.crud.core.capability.command.patch.EntityPatch;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 普通单表 UPDATE/PATCH 场景的强类型模板基类。
 *
 * @param <T> 实体类型
 * @param <R> 业务返回类型
 */
public abstract class AbstractPatchUpdateSceneHandler<T, R>
    extends AbstractEntityCommandSupport<T> {
    protected AbstractPatchUpdateSceneHandler(EntityMetaRegistry entityMetaRegistry, Class<T> entityType, String scene) {
        this(entityMetaRegistry, new DefaultCommandPayloadBinder(), entityType, scene);
    }

    protected AbstractPatchUpdateSceneHandler(
        EntityMetaRegistry entityMetaRegistry,
        CommandPayloadBinder payloadBinder,
        Class<T> entityType,
        String scene
    ) {
        super(entityMetaRegistry, payloadBinder, entityType, scene);
    }

    @Override
    public final CommandOperation operation() {
        return CommandOperation.UPDATE;
    }

    @Override
    public Object handle(CommandSpec<Object> spec, SceneDelegate<CommandSpec<Object>, Object> delegate) {
        Class<T> actualEntityType = requireEntityType();
        EntityMeta meta = entityMeta();
        EntityPatch<T> patch = requirePayloadBinder().bindEntityPatch(
            spec.getPayload(),
            actualEntityType,
            meta,
            additionalPatchFields()
        );
        if (patch.getId() == null && !hasFilters(spec.getTargetFilters())) {
            throw new ValidationException("update patch 需要合法 id 或 targetFilters");
        }
        CommandSpec<EntityPatch<T>> patchSpec = toPatchSpec(spec, patch);
        return handlePatch(patchSpec, delegate);
    }

    protected abstract R handlePatch(
        CommandSpec<EntityPatch<T>> spec,
        SceneDelegate<CommandSpec<Object>, Object> delegate
    );

    protected Set<String> additionalPatchFields() {
        return Collections.emptySet();
    }

    protected Object invokeDelegateUpdate(
        CommandSpec<EntityPatch<T>> spec,
        SceneDelegate<CommandSpec<Object>, Object> delegate
    ) {
        return invokeDelegateUpdate(spec, delegate, spec.getPayload().getValuesForDelegate());
    }

    protected Object invokeDelegateUpdate(
        CommandSpec<EntityPatch<T>> spec,
        SceneDelegate<CommandSpec<Object>, Object> delegate,
        Map<String, Object> values
    ) {
        EntityPatch<T> patch = spec.getPayload();
        Map<String, Object> writeValues = values == null
            ? Collections.<String, Object>emptyMap()
            : new LinkedHashMap<String, Object>(values);
        WriteCommand<Map<String, Object>> writeCommand = new WriteCommand<Map<String, Object>>(
            CommandOperation.UPDATE,
            patch.getId(),
            writeValues,
            spec.getTargetFilters(),
            spec.getExpectedVersion()
        );
        return delegate.invoke(toRawSpec(spec, writeCommand));
    }

    private CommandSpec<EntityPatch<T>> toPatchSpec(CommandSpec<Object> source, EntityPatch<T> patch) {
        return CommandSpec.<EntityPatch<T>>builder()
            .scene(source.getScene())
            .rootType(source.getRootType())
            .entityClasses(source.getEntityClasses())
            .subject(source.getSubject())
            .attributes(source.getAttributes())
            .grantedScope(source.getGrantedScope())
            .governanceScope(source.getGovernanceScope())
            .accessDecision(source.getAccessDecision())
            .op(source.getOp())
            .payload(patch)
            .idempotencyKey(source.getIdempotencyKey())
            .expectedVersion(source.getExpectedVersion())
            .dryRun(source.isDryRun())
            .targetFilters(source.getTargetFilters())
            .resultType(source.getResultType())
            .build();
    }

    private CommandSpec<Object> toRawSpec(CommandSpec<EntityPatch<T>> source, Object payload) {
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

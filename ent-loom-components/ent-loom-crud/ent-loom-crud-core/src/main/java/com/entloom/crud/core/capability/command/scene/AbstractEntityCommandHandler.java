package com.entloom.crud.core.capability.command.scene;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.core.capability.command.patch.CommandPayloadBinder;
import com.entloom.crud.core.capability.command.patch.DefaultCommandPayloadBinder;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import java.util.Collections;
import java.util.Set;

/**
 * 普通实体命令场景的强类型模板基类。
 *
 * @param <T> 实体类型
 * @param <R> 业务返回类型
 */
public abstract class AbstractEntityCommandHandler<T, R>
    extends AbstractEntityCommandSupport<T> {

    protected AbstractEntityCommandHandler() {
        super();
    }

    protected AbstractEntityCommandHandler(EntityMetaRegistry entityMetaRegistry, Class<T> entityType, String scene) {
        this(entityMetaRegistry, new DefaultCommandPayloadBinder(), entityType, scene);
    }

    protected AbstractEntityCommandHandler(
        EntityMetaRegistry entityMetaRegistry,
        CommandPayloadBinder payloadBinder,
        Class<T> entityType,
        String scene
    ) {
        super(entityMetaRegistry, payloadBinder, entityType, scene);
    }

    @Override
    public Object handle(CommandSpec<Object> spec, SceneDelegate<CommandSpec<Object>, Object> delegate) {
        Class<T> actualEntityType = requireEntityType();
        EntityMeta meta = requireEntityMetaRegistry().getEntityMeta(actualEntityType);
        T requested = requirePayloadBinder().bindEntity(spec.getPayload(), actualEntityType, meta, additionalEntityFields());
        if (requested == null) {
            throw new ValidationException(entityRequiredMessage());
        }
        beforeHandleEntity(requested, spec, meta);
        return handleEntity(requested);
    }

    @Override
    public abstract CommandOperation operation();

    protected abstract R handleEntity(T requested);

    protected String entityRequiredMessage() {
        return operation().name() +" entity 不能为空";
    }

    protected Set<String> additionalEntityFields() {
        return Collections.emptySet();
    }

    protected void beforeHandleEntity(T requested, CommandSpec<Object> spec, EntityMeta meta) {
    }
}

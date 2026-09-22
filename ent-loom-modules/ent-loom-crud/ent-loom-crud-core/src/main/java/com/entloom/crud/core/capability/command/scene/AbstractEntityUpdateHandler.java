package com.entloom.crud.core.capability.command.scene;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.core.capability.command.patch.CommandPayloadBinder;
import com.entloom.crud.core.capability.command.patch.DefaultCommandPayloadBinder;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.runtime.contract.CrudInputContract;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 普通实体 UPDATE 场景的强类型模板基类。
 *
 * @param <T> 实体类型
 * @param <R> 业务返回类型
 */
public abstract class AbstractEntityUpdateHandler<T, R>
    extends AbstractEntityCommandHandler<T, R> {
    private final CrudInputContract inputContract;

    protected AbstractEntityUpdateHandler() {
        this(CrudInputContract.empty());
    }

    protected AbstractEntityUpdateHandler(CrudInputContract inputContract) {
        super();
        this.inputContract = inputContract == null ? CrudInputContract.empty() : inputContract;
    }

    protected AbstractEntityUpdateHandler(EntityMetaRegistry entityMetaRegistry, Class<T> entityType, String scene) {
        this(entityMetaRegistry, new DefaultCommandPayloadBinder(), entityType, scene, CrudInputContract.empty());
    }

    protected AbstractEntityUpdateHandler(
        EntityMetaRegistry entityMetaRegistry,
        Class<T> entityType,
        String scene,
        CrudInputContract inputContract
    ) {
        this(entityMetaRegistry, new DefaultCommandPayloadBinder(), entityType, scene, inputContract);
    }

    protected AbstractEntityUpdateHandler(
        EntityMetaRegistry entityMetaRegistry,
        CommandPayloadBinder payloadBinder,
        Class<T> entityType,
        String scene
    ) {
        this(entityMetaRegistry, payloadBinder, entityType, scene, CrudInputContract.empty());
    }

    protected AbstractEntityUpdateHandler(
        EntityMetaRegistry entityMetaRegistry,
        CommandPayloadBinder payloadBinder,
        Class<T> entityType,
        String scene,
        CrudInputContract inputContract
    ) {
        super(entityMetaRegistry, payloadBinder, entityType, scene);
        this.inputContract = inputContract == null ? CrudInputContract.empty() : inputContract;
    }

    @Override
    public final CommandOperation operation() {
        return CommandOperation.UPDATE;
    }

    /** 返回当前处理器使用的外部输入契约，Spring 子类可通过注入覆盖。 */
    protected CrudInputContract inputContract() {
        return inputContract;
    }

    @Override
    protected final void beforeHandleEntity(
        T requested,
        CommandSpec<Object> spec,
        EntityMeta meta,
        Set<String> presentFields
    ) {
        Set<String> fields = presentFields == null
            ? java.util.Collections.<String>emptySet()
            : new LinkedHashSet<String>(presentFields);
        inputContract().validateUpdateFields(fields, meta);
        beforeHandleEntity(requested, spec, meta);
    }
}

package com.entloom.crud.core.capability.command.scene;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.core.capability.command.patch.CommandPayloadBinder;
import com.entloom.crud.core.capability.command.patch.DefaultCommandPayloadBinder;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import com.entloom.crud.core.util.RouteKeyFactory;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Set;

/**
 * 实体类 Command 场景模板的公共基础设施。
 *
 * @param <T> 实体类型
 */
public abstract class AbstractEntityCommandSupport<T> implements CommandSceneHandler<Object, Object> {
    private EntityMetaRegistry entityMetaRegistry;
    private CommandPayloadBinder payloadBinder;
    private Class<T> entityType;
    private String scene;
    private volatile Set<CrudRouteKey> routeKeys;

    protected AbstractEntityCommandSupport() {
        this.payloadBinder = new DefaultCommandPayloadBinder();
    }

    protected AbstractEntityCommandSupport(EntityMetaRegistry entityMetaRegistry, CommandPayloadBinder payloadBinder) {
        if (entityMetaRegistry == null) {
            throw new IllegalArgumentException("entityMetaRegistry 不能为空");
        }
        if (payloadBinder == null) {
            throw new IllegalArgumentException("payloadBinder 不能为空");
        }
        this.entityMetaRegistry = entityMetaRegistry;
        this.payloadBinder = payloadBinder;
    }

    protected AbstractEntityCommandSupport(EntityMetaRegistry entityMetaRegistry, Class<T> entityType, String scene) {
        this(entityMetaRegistry, new DefaultCommandPayloadBinder(), entityType, scene);
    }

    protected AbstractEntityCommandSupport(
        EntityMetaRegistry entityMetaRegistry,
        CommandPayloadBinder payloadBinder,
        Class<T> entityType,
        String scene
    ) {
        this(entityMetaRegistry, payloadBinder);
        if (entityType == null) {
            throw new IllegalArgumentException("entityType 不能为空");
        }
        this.entityType = entityType;
        this.scene = scene;
    }

    @Override
    public Set<CrudRouteKey> routeKeys() {
        Set<CrudRouteKey> cached = routeKeys;
        if (cached == null) {
            cached = Collections.singleton(
                new CrudRouteKey(
                    Collections.singletonList(requireEntityType().getName()),
                    CrudOperationKey.of(requireOperation()),
                    RouteKeyFactory.normalizeScene(scene())
                )
            );
            routeKeys = cached;
        }
        return cached;
    }

    protected EntityMeta entityMeta() {
        return requireEntityMetaRegistry().getEntityMeta(requireEntityType());
    }

    protected EntityMetaRegistry getEntityMetaRegistry() {
        return entityMetaRegistry;
    }

    protected CommandPayloadBinder getPayloadBinder() {
        return payloadBinder;
    }

    protected Class<T> getEntityType() {
        return entityType == null ? inferEntityType() : entityType;
    }

    protected String scene() {
        return scene;
    }

    protected final EntityMetaRegistry requireEntityMetaRegistry() {
        EntityMetaRegistry registry = getEntityMetaRegistry();
        if (registry == null) {
            throw new IllegalStateException("entityMetaRegistry 不能为空");
        }
        return registry;
    }

    protected final CommandPayloadBinder requirePayloadBinder() {
        CommandPayloadBinder binder = getPayloadBinder();
        if (binder == null) {
            throw new IllegalStateException("payloadBinder 不能为空");
        }
        return binder;
    }

    protected final Class<T> requireEntityType() {
        Class<T> type = getEntityType();
        if (type == null) {
            throw new IllegalStateException("entityType 不能为空");
        }
        return type;
    }

    protected final CommandOperation requireOperation() {
        CommandOperation commandOperation = operation();
        if (commandOperation == null) {
            throw new IllegalStateException("operation 不能为空");
        }
        return commandOperation;
    }

    @SuppressWarnings("unchecked")
    private Class<T> inferEntityType() {
        Class<?> current = getClass();
        while (current != null && current != Object.class) {
            Type genericSuperclass = current.getGenericSuperclass();
            if (genericSuperclass instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) genericSuperclass;
                Type rawType = parameterizedType.getRawType();
                if (rawType instanceof Class<?>
                    && AbstractEntityCommandSupport.class.isAssignableFrom((Class<?>) rawType)) {
                    Type entityArgument = parameterizedType.getActualTypeArguments()[0];
                    if (entityArgument instanceof Class<?>) {
                        return (Class<T>) entityArgument;
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}

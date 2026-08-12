package com.entloom.crud.starter.command.scene;

import com.entloom.crud.core.capability.command.scene.AbstractTargetDeleteHandler;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring 环境下带默认事务边界的实体 DELETE 场景模板。
 *
 * @param <T> 实体类型
 * @param <R> 业务返回类型
 */
public abstract class CrudTransactionalDeleteHandler<T, R>
    extends AbstractTargetDeleteHandler<T, R> {

    @Autowired
    private EntityMetaRegistry autowiredEntityMetaRegistry;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Object handle(CommandSpec<Object> spec, SceneDelegate<CommandSpec<Object>, Object> delegate) {
        return super.handle(spec, delegate);
    }

    @Override
    protected EntityMetaRegistry getEntityMetaRegistry() {
        EntityMetaRegistry registry = super.getEntityMetaRegistry();
        return registry == null ? autowiredEntityMetaRegistry : registry;
    }
}

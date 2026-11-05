package com.entloom.crud.core.capability.command.gateway;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.model.EntityWriteResult;
import com.entloom.crud.core.capability.command.patch.UpdatePatch;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.command.spec.DeleteTarget;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import com.entloom.crud.core.capability.dao.EntityType;
import java.util.Collections;

/**
 * 命令网关。
 */
public interface CommandGateway {
    /**
     * 执行业务动作。
     *
     * @param spec 命令协议
     * @param <P> 入参类型
     * @param <R> 返回类型
     * @return 执行结果
     */
    <P, R> R action(CommandSpec<P> spec);

    /** 创建实体；框架负责归属填充、字段校验及主键生成。 */
    default <T, ID> EntityWriteResult<ID> create(EntityType<T, ID> type, T entity) {
        return action(CommandSpec.<T>builder().rootType(type.entityClass()).idType(type.idClass()).op(CommandOperation.CREATE)
            .payload(entity).resultType(EntityWriteResult.class).build());
    }

    /** 按明确目标执行类型化局部更新；未提交字段保持不变。 */
    default <T, ID> EntityWriteResult<ID> update(EntityType<T, ID> type, ID id, UpdatePatch<T> patch) {
        return action(CommandSpec.<WriteCommand<UpdatePatch<T>>>builder().rootType(type.entityClass()).idType(type.idClass())
            .op(CommandOperation.UPDATE).payload(new WriteCommand<>(CommandOperation.UPDATE, id, patch))
            .expectedVersion(patch.getExpectedVersion()).resultType(EntityWriteResult.class).build());
    }

    default <T, ID> EntityWriteResult<ID> delete(EntityType<T, ID> type, ID id) {
        return delete(type, id, null);
    }

    /** 启用乐观锁的实体须传入预期版本。 */
    default <T, ID> EntityWriteResult<ID> delete(EntityType<T, ID> type, ID id, Long expectedVersion) {
        return action(CommandSpec.<DeleteTarget>builder().rootType(type.entityClass()).idType(type.idClass()).op(CommandOperation.DELETE)
            .payload(new DeleteTarget(id, Collections.emptyList(), expectedVersion))
            .resultType(EntityWriteResult.class).build());
    }
}

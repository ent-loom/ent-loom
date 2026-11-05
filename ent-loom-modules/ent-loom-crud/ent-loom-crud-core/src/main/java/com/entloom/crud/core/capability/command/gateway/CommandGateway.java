package com.entloom.crud.core.capability.command.gateway;

import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.model.CommandAction;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.api.model.EntityWriteResult;
import com.entloom.crud.core.capability.command.patch.UpdatePatch;
import com.entloom.crud.core.capability.command.handler.CommandActionResults;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.command.spec.DeleteTarget;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.exception.ValidationException;
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

    /** 类型化业务调用，直接取得业务数据；需要状态与重放标记时使用 actionResult。 */
    default <P, R> R action(CommandAction<P, R> action, P input) {
        return actionResult(action, input).getData();
    }

    /** 类型化动作结果，成功时保留业务数据、消息和重放标记；失败统一抛异常并回滚。 */
    default <P, R> CommandResult<R> actionResult(CommandAction<P, R> action, P input) {
        java.util.Objects.requireNonNull(action, "动作定义不能为空");
        if (input != null && !action.getRequestType().isInstance(input)) {
            throw new ValidationException("动作请求类型不匹配: " + action.getScene());
        }
        CommandResult<?> result = action(CommandSpec.<P>builder()
            .rootType(action.getEntityClass()).op(CommandOperation.ACTION).scene(action.getScene())
            .payload(input).resultType(CommandResult.class).build());
        return CommandActionResults.requireSuccess(result, action.getResponseType());
    }

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

package com.example.minicommerce.order.handler;

import com.entloom.crud.annotations.EntCrudCommandAction;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.core.capability.command.engine.AuthorizedActionContext;
import com.entloom.crud.core.capability.command.patch.DefaultCommandPayloadBinder;
import com.entloom.crud.core.capability.command.scene.CommandActionSceneHandler;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.dao.*;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.example.minicommerce.common.exception.BusinessException;
import com.example.minicommerce.order.dto.CancelOrderCommand;
import com.example.minicommerce.order.entity.Order;
import com.example.minicommerce.order.enums.*;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 范围及原状态进入实际写入条件，避免跨归属修改与并发重复取消。 */
@Component
@RequiredArgsConstructor
@EntCrudCommandAction(entityClass = Order.class, scene = Order.CANCEL)
public class CancelOrderHandler implements CommandActionSceneHandler<CancelOrderCommand, Long> {
    private final EntityDaoFactory daos;
    private final EntityMetaRegistry metas;

    @Override
    public CommandResult<Long> handle(CommandSpec<CancelOrderCommand> spec,
        SceneDelegate<CommandSpec<CancelOrderCommand>, CommandResult<Long>> delegate) {
        var access = AuthorizedActionContext.require(spec);
        var type = EntityType.of(Order.class, Long.class);
        var scoped = access.dao(daos, type);
        Long id = spec.getPayload().orderId();
        Order order = scoped.findById(id).orElseThrow(() -> new BusinessException(OrderError.ORDER_NOT_FOUND));
        if (order.getStatus() != OrderStatus.CREATED) throw new BusinessException(OrderError.ORDER_NOT_CANCELLABLE);
        // 将状态条件与当前授权范围取交集，最终 UPDATE 仍同时校验两者。
        var constrained = access.dao(daos, type, RowConstraint.eq("status", OrderStatus.CREATED));
        var patch = new DefaultCommandPayloadBinder().bindUpdatePatch(Map.of("id", id, "status", OrderStatus.CANCELLED),
            Order.class, metas.getEntityMeta(Order.class));
        constrained.updateById(id, patch);
        return CommandResult.success(id);
    }
}

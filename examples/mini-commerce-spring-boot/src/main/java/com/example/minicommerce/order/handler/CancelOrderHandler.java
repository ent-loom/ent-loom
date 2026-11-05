package com.example.minicommerce.order.handler;

import com.entloom.crud.annotations.EntCrudCommandAction;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.core.capability.command.engine.AuthorizedActionContext;
import com.entloom.crud.core.capability.command.scene.CommandActionSceneHandler;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.example.minicommerce.order.dto.CancelOrderCommand;
import com.example.minicommerce.order.entity.Order;
import com.example.minicommerce.order.service.OrderApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@EntCrudCommandAction(entityClass = Order.class, scene = Order.CANCEL, name = "取消订单",
    capability = "cancel-order", accessEntries = "commerce")
public class CancelOrderHandler implements CommandActionSceneHandler<CancelOrderCommand, Long> {
    private final OrderApplicationService orderApplicationService;

    @Override
    public CommandResult<Long> handle(CommandSpec<CancelOrderCommand> spec,
        SceneDelegate<CommandSpec<CancelOrderCommand>, CommandResult<Long>> delegate) {
        return CommandResult.success(orderApplicationService.cancel(AuthorizedActionContext.require(spec), spec.getPayload().orderId()));
    }
}

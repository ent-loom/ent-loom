package com.example.minicommerce.order.handler;

import com.entloom.crud.annotations.EntCrudCommandAction;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.core.capability.command.engine.AuthorizedActionContext;
import com.entloom.crud.core.capability.command.scene.CommandActionSceneHandler;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.example.minicommerce.order.dto.CancelOrderCommand;
import com.example.minicommerce.order.action.CommerceOrderActions;
import com.example.minicommerce.order.service.command.CommerceOrderCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@EntCrudCommandAction(value = CommerceOrderActions.CancelOrder.class, name = "取消订单",
    capability = "cancel-order", accessEntries = "commerce")
public class CancelOrderHandler implements CommandActionSceneHandler<CancelOrderCommand, Long> {
    private final CommerceOrderCommandService commerceOrderCommandService;

    @Override
    public CommandResult<Long> handle(CommandSpec<CancelOrderCommand> spec,
        SceneDelegate<CommandSpec<CancelOrderCommand>, CommandResult<Long>> delegate) {
        return CommandResult.success(commerceOrderCommandService.cancel(AuthorizedActionContext.require(spec), spec.getPayload().orderId()));
    }
}

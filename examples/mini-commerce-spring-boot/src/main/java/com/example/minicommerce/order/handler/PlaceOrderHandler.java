package com.example.minicommerce.order.handler;

import com.entloom.crud.annotations.EntCrudCommandAction;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.core.capability.command.engine.AuthorizedActionContext;
import com.entloom.crud.core.capability.command.scene.CommandActionSceneHandler;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.example.minicommerce.order.dto.PlaceOrderCommand;
import com.example.minicommerce.order.dto.PlaceOrderResult;
import com.example.minicommerce.order.entity.Order;
import com.example.minicommerce.order.service.OrderApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@EntCrudCommandAction(entityClass = Order.class, scene = Order.CREATE, name = "下单",
    capability = "place-order", accessEntries = "commerce")
public class PlaceOrderHandler implements CommandActionSceneHandler<PlaceOrderCommand, PlaceOrderResult> {
    private final OrderApplicationService orderApplicationService;

    @Override
    public CommandResult<PlaceOrderResult> handle(CommandSpec<PlaceOrderCommand> spec,
        SceneDelegate<CommandSpec<PlaceOrderCommand>, CommandResult<PlaceOrderResult>> delegate) {
        return CommandResult.success(orderApplicationService.place(AuthorizedActionContext.require(spec), spec.getPayload()));
    }
}

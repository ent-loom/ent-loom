package com.example.minicommerce.order.web;

import static com.example.minicommerce.order.action.CommerceOrderActions.PLACE_ORDER;

import com.entloom.crud.annotations.EntAccess;
import com.entloom.crud.api.model.CrudResponse;
import com.entloom.crud.core.capability.command.gateway.CommandGateway;
import com.example.minicommerce.order.dto.PlaceOrderCommand;
import com.example.minicommerce.order.dto.PlaceOrderResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 下单定制 HTTP 契约；授权、业务动作和事务复用既有执行链，取消与查询使用通用入口。 */
@RestController
@RequestMapping("/api/orders")
@EntAccess(accessEntries = "commerce")
@RequiredArgsConstructor
public class CommerceOrderController {
    private final CommandGateway commandGateway;

    @PostMapping
    public CrudResponse<PlaceOrderResult> place(@Valid @RequestBody PlaceOrderCommand input) {
        return CrudResponse.from(commandGateway.actionResult(PLACE_ORDER, input));
    }
}

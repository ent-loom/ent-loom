package com.example.minicommerce.order.action;

import com.entloom.crud.api.model.CommandAction;
import com.example.minicommerce.order.dto.CancelOrderCommand;
import com.example.minicommerce.order.dto.PlaceOrderCommand;
import com.example.minicommerce.order.dto.PlaceOrderResult;
import com.example.minicommerce.order.entity.CommerceOrder;

/** 订单动作契约：HTTP 调用方与 Handler 注册共用，不承载业务实现。 */
public final class CommerceOrderActions {
    public static final CommandAction<PlaceOrderCommand, PlaceOrderResult> PLACE_ORDER = new PlaceOrder();

    public static final CommandAction<CancelOrderCommand, Long> CANCEL_ORDER = new CancelOrder();

    private CommerceOrderActions() { }

    public static final class PlaceOrder extends CommandAction<PlaceOrderCommand, PlaceOrderResult> {
        public PlaceOrder() {
            super(CommerceOrder.class, CommerceOrder.CREATE, PlaceOrderCommand.class, PlaceOrderResult.class);
        }
    }

    public static final class CancelOrder extends CommandAction<CancelOrderCommand, Long> {
        public CancelOrder() {
            super(CommerceOrder.class, CommerceOrder.CANCEL, CancelOrderCommand.class, Long.class);
        }
    }
}

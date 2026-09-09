package com.example.minicommerce.order.web;

import com.example.minicommerce.order.application.PlaceOrderCommand;
import com.example.minicommerce.order.application.PlaceOrderHandler;
import com.example.minicommerce.order.application.PlaceOrderResult;
import com.example.minicommerce.order.application.OrderQueryService;
import com.example.minicommerce.order.model.OrderDetail;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 商城业务动作入口；订单不暴露给通用 CRUD。 */
@RestController
@RequestMapping("/orders")
public class OrderController {
    private final PlaceOrderHandler placeOrderHandler;
    private final OrderQueryService orderQueryService;

    public OrderController(PlaceOrderHandler placeOrderHandler, OrderQueryService orderQueryService) {
        this.placeOrderHandler = placeOrderHandler;
        this.orderQueryService = orderQueryService;
    }

    /** 提交订单。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlaceOrderResult placeOrder(@RequestBody PlaceOrderCommand command) {
        return placeOrderHandler.handle(command);
    }

    /** 查询订单及明细。 */
    @GetMapping("/{id}")
    public OrderDetail detail(@PathVariable Long id) {
        return orderQueryService.findDetail(id);
    }
}

package com.example.minicommerce.order.controller;

import com.example.minicommerce.order.dto.PlaceOrderCommand;
import com.example.minicommerce.order.service.PlaceOrderService;
import com.example.minicommerce.order.dto.PlaceOrderResult;
import com.example.minicommerce.order.service.OrderQueryService;
import com.example.minicommerce.order.dto.OrderDetail;
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
    private final PlaceOrderService placeOrderService;
    private final OrderQueryService orderQueryService;

    public OrderController(PlaceOrderService placeOrderService, OrderQueryService orderQueryService) {
        this.placeOrderService = placeOrderService;
        this.orderQueryService = orderQueryService;
    }

    /** 提交订单。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlaceOrderResult placeOrder(@RequestBody PlaceOrderCommand command) {
        return placeOrderService.handle(command);
    }

    /** 查询订单及明细。 */
    @GetMapping("/{id}")
    public OrderDetail detail(@PathVariable Long id) {
        return orderQueryService.findDetail(id);
    }
}

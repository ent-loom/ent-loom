package com.example.minicommerce.commerce;

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
public class CommerceController {
    private final PlaceOrderHandler placeOrderHandler;
    private final OrderRepository orderRepository;

    public CommerceController(PlaceOrderHandler placeOrderHandler, OrderRepository orderRepository) {
        this.placeOrderHandler = placeOrderHandler;
        this.orderRepository = orderRepository;
    }

    /** 提交订单。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlaceOrderResult placeOrder(@RequestBody PlaceOrderCommand command) {
        return placeOrderHandler.handle(command);
    }

    /** 查询订单及明细。 */
    @GetMapping("/{id}")
    public OrderDetailResponse detail(@PathVariable Long id) {
        return orderRepository.findDetail(id)
            .orElseThrow(() -> new CommerceValidationException("ORDER_NOT_FOUND", "订单不存在"));
    }
}

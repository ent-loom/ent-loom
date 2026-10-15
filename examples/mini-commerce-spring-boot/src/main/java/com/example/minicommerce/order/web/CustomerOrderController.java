package com.example.minicommerce.order.web;

import com.entloom.crud.annotations.EntAccess;
import com.entloom.crud.api.access.AccessContext;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.api.model.CrudResponse;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.starter.web.support.CrudResponseBuilder;
import com.example.minicommerce.order.dto.CustomerOrderPageQuery;
import com.example.minicommerce.order.dto.OrderDetail;
import com.example.minicommerce.order.dto.OrderDetailQuery;
import com.example.minicommerce.order.dto.PlaceOrderCommand;
import com.example.minicommerce.order.dto.PlaceOrderResult;
import com.example.minicommerce.order.service.OrderQueryService;
import com.example.minicommerce.order.service.PlaceOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 客户端订单入口：本人下单、分页和详情。 */
@RestController
@RequestMapping("/api/consumer/orders")
@EntAccess(identity = "customer", accessEntries = "consumer", subjectTypes = AccessSubjectType.HUMAN)
@RequiredArgsConstructor
public class CustomerOrderController {
    private final OrderQueryService orders;
    private final PlaceOrderService placeOrderService;
    private final CrudResponseBuilder responses;

    @PostMapping("/place")
    public CrudResponse<PlaceOrderResult> place(AccessContext access,
        @Valid @RequestBody PlaceOrderCommand command) {
        return responses.success(placeOrderService.place(access.getVisitorContext(), command));
    }

    @PostMapping("/page")
    public CrudResponse<PageResult<CrudRecord>> page(AccessContext access,
        @Valid @RequestBody CustomerOrderPageQuery query) {
        return responses.success(orders.customerPage(access.getVisitorContext(), query));
    }

    @PostMapping("/detail")
    public CrudResponse<OrderDetail> detail(AccessContext access,
        @Valid @RequestBody OrderDetailQuery query) {
        return responses.success(orders.detail(access.getVisitorContext(), query.orderId()));
    }
}

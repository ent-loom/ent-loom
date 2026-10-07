package com.example.minicommerce.order.web;

import com.entloom.crud.annotations.EntAccess;
import com.entloom.crud.api.access.AccessContext;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.api.model.CrudResponse;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.starter.web.support.CrudResponseBuilder;
import com.example.minicommerce.customer.access.CustomerIdentity;
import com.example.minicommerce.order.dto.OrderPageQueries;
import com.example.minicommerce.order.service.OrderPageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户端分页：仅查询本人子订单，允许进一步按商家筛选。 */
@RestController
@RequestMapping("/api/consumer/orders")
@EntAccess(identity = CustomerIdentity.class, accessEntries = "consumer", subjectTypes = AccessSubjectType.HUMAN)
@RequiredArgsConstructor
public class ConsumerOrderController {
    private final OrderPageService orders;
    private final CrudResponseBuilder responses;

    @PostMapping("/page")
    public CrudResponse<PageResult<CrudRecord>> page(AccessContext<CustomerIdentity> access,
        @Valid @RequestBody OrderPageQueries.Consumer query) {
        return responses.success(orders.consumerPage(access.getVisitorContext(), query));
    }
}

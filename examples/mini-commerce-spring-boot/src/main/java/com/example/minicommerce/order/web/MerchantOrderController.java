package com.example.minicommerce.order.web;

import com.entloom.crud.annotations.EntAccess;
import com.entloom.crud.api.access.AccessContext;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.api.model.CrudResponse;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.starter.web.support.CrudResponseBuilder;
import com.example.minicommerce.order.dto.MerchantOrderPageQuery;
import com.example.minicommerce.order.dto.OrderDetail;
import com.example.minicommerce.order.dto.OrderDetailQuery;
import com.example.minicommerce.order.service.OrderQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 商家端订单入口：仅查询本商家订单。 */
@RestController
@RequestMapping("/api/merchant/orders")
@EntAccess(identity = "merchant", accessEntries = "merchant", subjectTypes = AccessSubjectType.HUMAN)
@RequiredArgsConstructor
public class MerchantOrderController {
    private final OrderQueryService orders;
    private final CrudResponseBuilder responses;

    @PostMapping("/page")
    public CrudResponse<PageResult<CrudRecord>> page(AccessContext access,
        @Valid @RequestBody MerchantOrderPageQuery query) {
        return responses.success(orders.merchantPage(access.getVisitorContext(), query));
    }

    @PostMapping("/detail")
    public CrudResponse<OrderDetail> detail(AccessContext access,
        @Valid @RequestBody OrderDetailQuery query) {
        return responses.success(orders.detail(access.getVisitorContext(), query.orderId()));
    }
}

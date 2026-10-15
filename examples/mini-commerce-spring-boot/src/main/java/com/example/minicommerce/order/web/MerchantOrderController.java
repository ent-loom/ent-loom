package com.example.minicommerce.order.web;

import com.entloom.crud.annotations.EntAccess;
import com.entloom.crud.api.access.AccessContext;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.api.model.CrudResponse;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.starter.web.support.CrudResponseBuilder;
import com.example.minicommerce.order.dto.MerchantOrderPageQuery;
import com.example.minicommerce.order.service.MerchantOrderQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 商家端分页：仅查询本商家销售订单，允许进一步按客户筛选。 */
@RestController
@RequestMapping("/api/merchant/orders")
@EntAccess(identity = "merchant", accessEntries = "merchant", subjectTypes = AccessSubjectType.HUMAN)
@RequiredArgsConstructor
public class MerchantOrderController {
    private final MerchantOrderQueryService orders;
    private final CrudResponseBuilder responses;

    @PostMapping("/page")
    public CrudResponse<PageResult<CrudRecord>> page(AccessContext access,
        @Valid @RequestBody MerchantOrderPageQuery query) {
        return responses.success(orders.merchantPage(access.getVisitorContext(), query));
    }
}

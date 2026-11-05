package com.example.minicommerce.merchant.web;

import com.entloom.crud.annotations.EntAccess;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.model.CrudResponse;
import com.entloom.crud.starter.web.support.CrudResponseBuilder;
import com.example.minicommerce.merchant.service.ProductSummaryService;
import com.example.minicommerce.merchant.service.ProductSummaryService.Page;
import com.example.minicommerce.merchant.service.ProductSummaryService.Query;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 商家商品摘要的业务 DTO 入口。 */
@RestController
@RequestMapping("/api/merchant/products")
@EntAccess(accessEntries = "merchant")
@RequiredArgsConstructor
public class ProductSummaryController {
    private final ProductSummaryService products;
    private final CrudResponseBuilder responses;

    @PostMapping("/page")
    public CrudResponse<Page> page(VisitorContext visitor, @Valid @RequestBody Query query) {
        CrudResponse<Page> response = responses.success(products.page(visitor, query));
        response.setRequestId(visitor.getRequestId());
        response.setTraceId(visitor.getTraceId());
        return response;
    }
}

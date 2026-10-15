package com.example.minicommerce.merchant.web;

import com.entloom.crud.annotations.EntAccess;
import com.entloom.crud.api.access.AccessContext;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.api.model.CrudResponse;
import com.entloom.crud.starter.web.support.CrudResponseBuilder;
import com.example.minicommerce.merchant.dto.ChangePricesCommand;
import com.example.minicommerce.merchant.service.MerchantPricingService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 商家商品价格接口：当前身份来自框架准入，载荷仅接受商品 ID、价格和预期版本。 */
@RestController
@RequestMapping("/api/merchant/products")
@EntAccess(identity = "merchant", accessEntries = "merchant", subjectTypes = AccessSubjectType.HUMAN)
@RequiredArgsConstructor
public class MerchantPricingController {
    private final MerchantPricingService pricing;
    private final CrudResponseBuilder responses;

    @GetMapping
    public CrudResponse<List<CrudRecord>> list(AccessContext access) {
        return responses.success(pricing.list(access.getVisitorContext()));
    }

    @PostMapping("/prices")
    public CrudResponse<List<Long>> changePrices(AccessContext access,
        @Valid @RequestBody ChangePricesCommand request) {
        return responses.success(pricing.changePrices(access.getVisitorContext(), request.items()));
    }
}

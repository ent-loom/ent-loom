package com.example.minicommerce.merchant.web;

import com.entloom.crud.annotations.EntAccess;
import com.entloom.crud.api.access.AccessContext;
import com.entloom.crud.api.access.AccessSubjectType;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.api.model.CrudResponse;
import com.entloom.crud.core.exception.EntityDaoWriteMissException;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.starter.web.error.CrudHttpErrorMapper;
import com.entloom.crud.starter.web.support.CrudResponseBuilder;
import com.example.minicommerce.merchant.access.MerchantIdentity;
import com.example.minicommerce.merchant.dto.ChangePricesCommand;
import com.example.minicommerce.merchant.service.MerchantPricingService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 商家报价接口：当前身份来自框架准入，载荷仅接受报价 ID、价格和预期版本。 */
@RestController
@RequestMapping("/api/merchant/products")
@EntAccess(identity = MerchantIdentity.class, accessEntries = "merchant", subjectTypes = AccessSubjectType.HUMAN)
@RequiredArgsConstructor
public class MerchantPricingController {
    private final MerchantPricingService pricing;
    private final CrudResponseBuilder responses;

    @GetMapping
    public CrudResponse<List<CrudRecord>> list(AccessContext<MerchantIdentity> access) {
        return responses.success(pricing.list(access.getVisitorContext()));
    }

    @PostMapping("/prices")
    public CrudResponse<List<Long>> changePrices(AccessContext<MerchantIdentity> access,
        @Valid @RequestBody ChangePricesCommand request) {
        return responses.success(pricing.changePrices(access.getVisitorContext(), request.items()));
    }

    @ExceptionHandler(EntityDaoWriteMissException.class)
    ResponseEntity<CrudResponse<Void>> writeMiss(EntityDaoWriteMissException error) {
        // 不区分记录不存在、跨商家或版本过期，避免泄露其他商家的数据存在性。
        return ResponseEntity.status(409).body(responses.failure("PRICE_UPDATE_REJECTED",
            "报价不可更新或版本已变化，整批未生效；请重新读取本人报价"));
    }

    @ExceptionHandler(CrudException.class)
    ResponseEntity<CrudResponse<Void>> frameworkError(CrudException error) {
        var mapped = CrudHttpErrorMapper.map(error);
        return ResponseEntity.status(mapped.getStatus()).body(responses.failure(mapped.getError()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<CrudResponse<Void>> invalidRequest(Exception error) {
        return ResponseEntity.badRequest().body(responses.failure("VALIDATION_ERROR", "请提供 1～100 条合法报价、价格和版本"));
    }
}

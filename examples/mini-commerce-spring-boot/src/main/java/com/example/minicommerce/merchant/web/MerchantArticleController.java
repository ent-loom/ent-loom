package com.example.minicommerce.merchant.web;

import com.entloom.crud.annotations.EntAccess;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.SortDirection;
import com.entloom.crud.api.model.CrudResponse;
import com.entloom.crud.api.model.EntityWriteResult;
import com.entloom.crud.api.model.PageRequest;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.core.capability.command.gateway.CommandGateway;
import com.entloom.crud.core.capability.command.patch.UpdatePatch;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.capability.query.gateway.QueryGateway;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.example.minicommerce.merchant.dto.CreateMerchantArticle;
import com.example.minicommerce.merchant.dto.MerchantArticleSummary;
import com.example.minicommerce.merchant.entity.MerchantArticle;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 自定义 HTTP 契约；身份、归属、字段校验、持久化与审计复用 CRUD 内核。 */
@RestController
@RequestMapping("/api/merchant/articles")
@EntAccess(accessEntries = "merchant")
@RequiredArgsConstructor
public class MerchantArticleController {
    private static final EntityType<MerchantArticle, Long> ARTICLES = EntityType.of(MerchantArticle.class, Long.class);
    private final QueryGateway queryGateway;
    private final CommandGateway commandGateway;

    @PostMapping("/page")
    public CrudResponse<PageResult<MerchantArticleSummary>> page(@Valid @RequestBody Query query) {
        var spec = QuerySpec.builder(MerchantArticle.class, MerchantArticleSummary.class)
            .sorts(List.of(new QuerySort("id", SortDirection.ASC)))
            .page(new PageRequest(query.page(), query.limit())).build();
        return CrudResponse.success(queryGateway.page(spec));
    }

    @GetMapping("/{id}")
    public CrudResponse<MerchantArticle> detail(@PathVariable("id") long id) {
        var spec = QuerySpec.builder(MerchantArticle.class)
            .filters(List.of(new QueryFilter("id", FilterOperator.EQ, id))).build();
        return CrudResponse.success(queryGateway.detail(spec));
    }

    @PostMapping
    public CrudResponse<EntityWriteResult<Long>> create(@RequestBody CreateMerchantArticle input) {
        return CrudResponse.success(commandGateway.create(ARTICLES, input.toEntity()));
    }

    @PutMapping("/{id}")
    public CrudResponse<EntityWriteResult<Long>> update(@PathVariable("id") long id,
        @RequestBody UpdatePatch<MerchantArticle> patch) {
        return CrudResponse.success(commandGateway.update(ARTICLES, id, patch));
    }

    @DeleteMapping("/{id}")
    public CrudResponse<EntityWriteResult<Long>> delete(@PathVariable("id") long id) {
        return CrudResponse.success(commandGateway.delete(ARTICLES, id));
    }

    public record Query(@Min(1) Integer page, @Min(1) @Max(100) Integer limit) {
        public Query {
            page = page == null ? 1 : page;
            limit = limit == null ? 20 : limit;
        }

        @JsonAnySetter
        public void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("不支持的请求字段: " + name);
        }
    }
}

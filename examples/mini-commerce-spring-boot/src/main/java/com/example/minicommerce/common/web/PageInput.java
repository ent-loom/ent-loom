package com.example.minicommerce.common.web;

import com.entloom.crud.api.model.PageRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 业务接口通用分页参数。 */
public record PageInput(@Min(1) Integer page, @Min(1) @Max(100) Integer limit) {
    public PageInput {
        page = page == null ? 1 : page;
        limit = limit == null ? 20 : limit;
    }

    public PageRequest toPageRequest() {
        return new PageRequest(page, limit);
    }
}

package com.example.minicommerce.order.service;

import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.CountMode;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.enums.SortDirection;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.api.model.PageQuery;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.api.model.QuerySort;
import com.entloom.crud.core.capability.query.engine.AuthorizedQueryExecutor;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.example.minicommerce.order.dto.OrderPageQueries;
import com.example.minicommerce.order.entity.MerchantOrder;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 业务侧只构建分页与额外筛选；身份归属范围由框架合并到数据 SQL 和 count SQL。 */
@Service
@RequiredArgsConstructor
public class OrderPageService {
    private final AuthorizedQueryExecutor queries;

    public PageResult<CrudRecord> consumerPage(VisitorContext visitor, OrderPageQueries.Consumer query) {
        return page(visitor, query.pageNumber(), query.pageSize(), "merchantId", query.merchantId());
    }

    public PageResult<CrudRecord> merchantPage(VisitorContext visitor, OrderPageQueries.Merchant query) {
        return page(visitor, query.pageNumber(), query.pageSize(), "customerId", query.customerId());
    }

    private PageResult<CrudRecord> page(VisitorContext visitor, int number, int size, String field, Long value) {
        var page = new PageQuery(number, size, List.of(new QuerySort("id", SortDirection.ASC)), CountMode.ALWAYS);
        var filters = value == null ? List.<QueryFilter>of() : List.of(new QueryFilter(field, FilterOperator.EQ, value));
        return queries.page(visitor, QuerySpec.<CrudRecord>builder().rootType(MerchantOrder.class)
            .resultType(CrudRecord.class).op(QueryOperation.PAGE).pageQuery(page).filters(filters).build());
    }
}

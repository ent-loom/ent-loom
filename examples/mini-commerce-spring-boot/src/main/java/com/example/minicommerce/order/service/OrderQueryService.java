package com.example.minicommerce.order.service;

import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.CountMode;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.api.model.PageQuery;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.capability.query.engine.AuthorizedQueryExecutor;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.example.minicommerce.common.exception.BusinessException;
import com.example.minicommerce.order.dto.CustomerOrderPageQuery;
import com.example.minicommerce.order.dto.MerchantOrderPageQuery;
import com.example.minicommerce.order.dto.OrderDetail;
import com.example.minicommerce.order.entity.Order;
import com.example.minicommerce.order.enums.OrderError;
import com.example.minicommerce.order.repository.OrderRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 订单查询复用框架授权范围；详情校验根订单后再读取成交明细。 */
@Service
@RequiredArgsConstructor
public class OrderQueryService {
    private final AuthorizedQueryExecutor queries;
    private final OrderRepository repository;

    public OrderDetail detail(VisitorContext visitor, Long orderId) {
        CrudRecord order = queries.findOne(visitor, QuerySpec.<CrudRecord>builder().rootType(Order.class)
            .resultType(CrudRecord.class).op(QueryOperation.FIND_ONE)
            .filters(List.of(new QueryFilter("id", FilterOperator.EQ, orderId))).build());
        if (order == null) {
            throw new BusinessException(OrderError.ORDER_NOT_FOUND);
        }
        // 先由框架校验订单归属，再读取该订单的内部成交快照。
        return new OrderDetail(order, repository.findItems(orderId));
    }

    public PageResult<CrudRecord> customerPage(VisitorContext visitor, CustomerOrderPageQuery query) {
        return page(visitor, query.pageNumber(), query.pageSize(), "merchantId", query.merchantId());
    }

    public PageResult<CrudRecord> merchantPage(VisitorContext visitor, MerchantOrderPageQuery query) {
        return page(visitor, query.pageNumber(), query.pageSize(), "customerId", query.customerId());
    }

    private PageResult<CrudRecord> page(VisitorContext visitor, int number, int size, String field, Long value) {
        var page = new PageQuery(number, size, List.of(), CountMode.ALWAYS);
        var filters = value == null ? List.<QueryFilter>of() : List.of(new QueryFilter(field, FilterOperator.EQ, value));
        return queries.page(visitor, QuerySpec.<CrudRecord>builder().rootType(Order.class)
            .resultType(CrudRecord.class).op(QueryOperation.PAGE).pageQuery(page).filters(filters).build());
    }
}

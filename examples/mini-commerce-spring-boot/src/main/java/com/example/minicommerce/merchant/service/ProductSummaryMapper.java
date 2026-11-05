package com.example.minicommerce.merchant.service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 固定投影与参数化 SQL；业务 Service 必须先完成授权并选择明确范围。 */
@Repository
@RequiredArgsConstructor
class ProductSummaryMapper {
    private final JdbcTemplate jdbc;

    ProductSummaryService.Page pageAll(Boolean active, int page, int limit) {
        return page(null, active, page, limit, false);
    }

    ProductSummaryService.Page pageMerchant(long merchantId, Boolean active, int page, int limit) {
        return page(merchantId, active, page, limit, true);
    }

    private ProductSummaryService.Page page(Long merchantId, Boolean active, int page, int limit, boolean restricted) {
        List<String> conditions = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        if (restricted) {
            conditions.add("merchant_id = ?");
            parameters.add(merchantId);
        }
        if (active != null) {
            conditions.add("active = ?");
            parameters.add(active);
        }
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM merchant_product" + where, Long.class,
            parameters.toArray());
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(limit);
        pageParameters.add(((long) page - 1) * limit);
        List<ProductSummaryService.Summary> items = jdbc.query(
            "SELECT id, name, price FROM merchant_product" + where + " ORDER BY id ASC LIMIT ? OFFSET ?",
            (row, index) -> new ProductSummaryService.Summary(row.getLong("id"), row.getString("name"),
                row.getBigDecimal("price")), pageParameters.toArray());
        return new ProductSummaryService.Page(items, page, limit, total);
    }
}

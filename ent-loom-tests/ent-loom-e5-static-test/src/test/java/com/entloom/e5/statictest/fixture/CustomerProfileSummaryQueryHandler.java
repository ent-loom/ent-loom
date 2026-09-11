package com.entloom.e5.statictest.fixture;

import com.entloom.crud.annotations.EntCrudQueryHandler;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.core.capability.query.scene.QueryDetailSceneHandler;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.entloom.crud.core.util.RouteKeyFactory;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;

/** 客户档案复杂摘要场景；固定聚合 SQL 只在受治理 Handler 内执行。 */
@EntCrudQueryHandler(entityClasses = {CustomerProfile.class}, scenes = {"profile.summary"})
public final class CustomerProfileSummaryQueryHandler implements QueryDetailSceneHandler<CustomerProfileSummary> {
    private static final Set<CrudRouteKey> ROUTE_KEYS = Collections.singleton(
        new CrudRouteKey(
            Collections.singletonList(CustomerProfile.class.getName()),
            CrudOperationKey.of(QueryOperation.DETAIL),
            RouteKeyFactory.normalizeScene("profile.summary")
        )
    );

    private final JdbcTemplate jdbcTemplate;
    private final AtomicInteger handleCalls = new AtomicInteger();

    public CustomerProfileSummaryQueryHandler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Set<CrudRouteKey> routeKeys() {
        return ROUTE_KEYS;
    }

    @Override
    public CustomerProfileSummary handle(
        QuerySpec<CustomerProfileSummary> spec,
        SceneDelegate<QuerySpec<CustomerProfileSummary>, CustomerProfileSummary> delegate
    ) {
        handleCalls.incrementAndGet();
        Long profileId = profileId(spec);
        List<CustomerProfileSummary> rows = jdbcTemplate.query(
            "select p.id, p.display_name, p.credit_limit, p.registered_at, "
                + "count(n.id) as note_count, max(n.created_at) as latest_note_at "
                + "from customer_profile p "
                + "left join customer_profile_note n on n.profile_id = p.id "
                + "where p.id = ? "
                + "group by p.id, p.display_name, p.credit_limit, p.registered_at",
            (resultSet, rowNum) -> new CustomerProfileSummary(
                resultSet.getLong("id"),
                resultSet.getString("display_name"),
                resultSet.getBigDecimal("credit_limit"),
                resultSet.getTimestamp("registered_at").toLocalDateTime(),
                resultSet.getLong("note_count"),
                toLocalDateTime(resultSet.getTimestamp("latest_note_at"))
            ),
            profileId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int getHandleCalls() {
        return handleCalls.get();
    }

    private Long profileId(QuerySpec<CustomerProfileSummary> spec) {
        return spec.getFilters().stream()
            .filter(filter -> "id".equals(filter.getField()) && filter.getOperator() == FilterOperator.EQ)
            .map(filter -> filter.getValue())
            .findFirst()
            .map(this::toLong)
            .orElseThrow(() -> new ValidationException("profile.summary 必须包含 id EQ 条件"));
    }

    private Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new ValidationException("profile.summary 的 id 条件无效");
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}

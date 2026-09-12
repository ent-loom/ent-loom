package com.entloom.e5.statictest.fixture;

import com.entloom.crud.annotations.EntCrudQueryHandler;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.capability.query.scene.QueryDetailSceneHandler;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.exception.DataScopeDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.scope.CrudDataScope;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.router.CrudRouteKey;
import com.entloom.crud.core.runtime.scene.SceneDelegate;
import com.entloom.crud.core.util.RouteKeyFactory;
import com.entloom.crud.engine.jdbc.sql.JdbcPredicateBuilder;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    private final EntityMeta customerProfileMeta;
    private final AtomicInteger handleCalls = new AtomicInteger();

    public CustomerProfileSummaryQueryHandler(JdbcTemplate jdbcTemplate, EntityMetaRegistry metaRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.customerProfileMeta = metaRegistry.getEntityMeta(CustomerProfile.class);
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
        List<Object> args = new ArrayList<Object>();
        String sql = summarySql(spec.getGovernanceScope(), profileId, args);
        List<CustomerProfileSummary> rows = jdbcTemplate.query(
            sql,
            (resultSet, rowNum) -> new CustomerProfileSummary(
                resultSet.getLong("id"),
                resultSet.getString("display_name"),
                resultSet.getBigDecimal("credit_limit"),
                resultSet.getTimestamp("registered_at").toLocalDateTime(),
                resultSet.getLong("note_count"),
                toLocalDateTime(resultSet.getTimestamp("latest_note_at"))
            ),
            args.toArray()
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int getHandleCalls() {
        return handleCalls.get();
    }

    private Long profileId(QuerySpec<CustomerProfileSummary> spec) {
        List<QueryFilter> filters = spec.getFilters();
        if (filters.size() != 1) {
            throw new ValidationException("profile.summary 只允许一个 id EQ 条件");
        }
        QueryFilter filter = filters.get(0);
        if (!"id".equals(filter.getField()) || filter.getOperator() != FilterOperator.EQ) {
            throw new ValidationException("profile.summary 必须包含 id EQ 条件");
        }
        return toLong(filter.getValue());
    }

    private Long toLong(Object value) {
        try {
            if (value instanceof Number) {
                return new BigDecimal(value.toString()).longValueExact();
            }
            return Long.valueOf(String.valueOf(value));
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new ValidationException("profile.summary 的 id 条件无效");
        }
    }

    /** 将根实体治理范围编译到定制 SQL，保证场景查询不旁路默认引擎的范围语义。 */
    private String summarySql(CrudDataScope scope, Long profileId, List<Object> args) {
        List<String> predicates = new ArrayList<String>();
        predicates.add("p.id = ?");
        args.add(profileId);
        appendLogicDeletePredicate(predicates);
        appendGovernanceScopePredicates(scope, predicates, args);
        return "select p.id, p.display_name, p.credit_limit, p.registered_at, "
            + "count(n.id) as note_count, max(n.created_at) as latest_note_at "
            + "from customer_profile p "
            + "left join customer_profile_note n on n.profile_id = p.id "
            + "where " + String.join(" and ", predicates) + " "
            + "group by p.id, p.display_name, p.credit_limit, p.registered_at";
    }

    private void appendLogicDeletePredicate(List<String> predicates) {
        String field = customerProfileMeta.getLogicDeleteField();
        if (field != null && !field.trim().isEmpty()) {
            predicates.add("p." + customerProfileMeta.resolveColumn(field) + " = 0");
        }
    }

    private void appendGovernanceScopePredicates(CrudDataScope scope, List<String> predicates, List<Object> args) {
        if (scope == null) {
            throw new DataScopeDeniedException("profile.summary 缺少治理范围");
        }
        if (scope.isExplicitAll()) {
            return;
        }
        for (Map.Entry<String, Object> entry : scope.getDimensions().entrySet()) {
            String column = customerProfileMeta.resolveColumn(entry.getKey());
            if (column == null) {
                throw new DataScopeDeniedException("profile.summary 不支持治理范围维度: " + entry.getKey());
            }
            JdbcPredicateBuilder.appendEqualityOrIn(predicates, args, "p." + column, entry.getValue(), "governance scope");
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}

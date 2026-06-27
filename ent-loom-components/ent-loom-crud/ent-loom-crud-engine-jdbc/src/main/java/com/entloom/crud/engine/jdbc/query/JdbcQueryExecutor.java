package com.entloom.crud.engine.jdbc.query;

import com.entloom.crud.api.enums.PageCountMode;
import com.entloom.crud.api.model.PageResult;
import com.entloom.crud.core.runtime.context.DefaultExecutionContext;
import com.entloom.crud.core.exception.NotFoundException;
import com.entloom.crud.core.exception.QueryNotUniqueException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.foundation.read.relation.RelationLoadRequest;
import com.entloom.crud.core.foundation.read.relation.RelationLoader;
import com.entloom.crud.core.foundation.read.relation.RelationLoaderRegistry;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.capability.query.CompiledQuery;
import com.entloom.crud.core.capability.query.QueryExecutor;
import com.entloom.crud.core.security.GuardedSqlExecutor;
import com.entloom.crud.core.util.RouteKeyFactory;
import com.entloom.crud.enums.RelationScope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * JDBC 查询执行器。
 */
public class JdbcQueryExecutor implements QueryExecutor {
    /** 受保护的 SQL 执行器。 */
    private final GuardedSqlExecutor guardedSqlExecutor;
    /** 实体元数据注册表。 */
    private final EntityMetaRegistry metaRegistry;
    /** 反射映射器。 */
    private final JdbcReflectiveMapper reflectiveMapper;
    /** 特殊关系加载器注册表。 */
    private final RelationLoaderRegistry relationLoaderRegistry;

    public JdbcQueryExecutor(GuardedSqlExecutor guardedSqlExecutor, EntityMetaRegistry metaRegistry) {
        this(guardedSqlExecutor, metaRegistry, false);
    }

    public JdbcQueryExecutor(
        GuardedSqlExecutor guardedSqlExecutor,
        EntityMetaRegistry metaRegistry,
        boolean relationFieldFallbackEnabled
    ) {
        this(guardedSqlExecutor, metaRegistry, relationFieldFallbackEnabled, RelationLoaderRegistry.empty());
    }

    public JdbcQueryExecutor(
        GuardedSqlExecutor guardedSqlExecutor,
        EntityMetaRegistry metaRegistry,
        boolean relationFieldFallbackEnabled,
        RelationLoaderRegistry relationLoaderRegistry
    ) {
        this(
            guardedSqlExecutor,
            metaRegistry,
            new JdbcReflectiveMapper(relationFieldFallbackEnabled),
            relationLoaderRegistry
        );
    }

    public JdbcQueryExecutor(
        GuardedSqlExecutor guardedSqlExecutor,
        EntityMetaRegistry metaRegistry,
        RelationLoaderRegistry relationLoaderRegistry
    ) {
        this(guardedSqlExecutor, metaRegistry, new JdbcReflectiveMapper(), relationLoaderRegistry);
    }

    JdbcQueryExecutor(
        GuardedSqlExecutor guardedSqlExecutor,
        EntityMetaRegistry metaRegistry,
        JdbcReflectiveMapper reflectiveMapper
    ) {
        this(guardedSqlExecutor, metaRegistry, reflectiveMapper, RelationLoaderRegistry.empty());
    }

    JdbcQueryExecutor(
        GuardedSqlExecutor guardedSqlExecutor,
        EntityMetaRegistry metaRegistry,
        JdbcReflectiveMapper reflectiveMapper,
        RelationLoaderRegistry relationLoaderRegistry
    ) {
        this.guardedSqlExecutor = guardedSqlExecutor;
        this.metaRegistry = metaRegistry;
        this.reflectiveMapper = reflectiveMapper == null ? new JdbcReflectiveMapper() : reflectiveMapper;
        this.relationLoaderRegistry = relationLoaderRegistry == null ? RelationLoaderRegistry.empty() : relationLoaderRegistry;
    }

    @Override
    public <R> PageResult<R> executePage(CompiledQuery query, Class<R> viewType) {
        if (query.getQueryPlan().getSpec().getCountMode() == PageCountMode.NONE) {
            return executePageWithoutCount(query, viewType);
        }
        int page = query.getQueryPlan().getSpec().getPage().getPage();
        int limit = query.getQueryPlan().getSpec().getPage().getLimit();
        long total = executeCount(query);
        if (total <= 0L || isPageOutOfRange(page, limit, total)) {
            return new PageResult<>(Collections.<R>emptyList(), total, page, limit);
        }
        List<R> items = executeList(query, viewType);
        return new PageResult<>(items, total, page, limit);
    }

    @Override
    public <R> List<R> executeList(CompiledQuery query, Class<R> viewType) {
        List<R> result = executeMainRows(query, viewType);
        expandRelations(query, result);
        return result;
    }

    @Override
    public <R> R executeFindOne(CompiledQuery query, Class<R> viewType) {
        return executeSingle(query, viewType, RowCardinalityPolicy.AT_MOST_ONE);
    }

    @Override
    public <R> R executeOne(CompiledQuery query, Class<R> viewType) {
        return executeSingle(query, viewType, RowCardinalityPolicy.EXACTLY_ONE);
    }

    @Override
    public long executeCount(CompiledQuery query) {
        DefaultExecutionContext context = buildContext(query, "main");
        Object value = guardedSqlExecutor.queryForObject(query.getCountSql(), query.getCountArgs(), context);
        return value == null ? 0L : ((Number) value).longValue();
    }

    private <R> PageResult<R> executePageWithoutCount(CompiledQuery query, Class<R> viewType) {
        int page = query.getQueryPlan().getSpec().getPage().getPage();
        int limit = query.getQueryPlan().getSpec().getPage().getLimit();
        List<R> rows = executeMainRows(query, viewType);
        boolean hasNext = rows.size() > limit;
        List<R> items = rows;
        if (hasNext) {
            items = new ArrayList<R>(rows.subList(0, limit));
        }
        expandRelations(query, items);

        PageResult<R> result = new PageResult<>(items, 0L, page, limit);
        result.setTotalKnown(false);
        result.setHasNext(hasNext);
        return result;
    }

    private <R> List<R> executeMainRows(CompiledQuery query, Class<R> viewType) {
        DefaultExecutionContext context = buildContext(query, "main");
        List<Map<String, Object>> rows = guardedSqlExecutor.queryForList(query.getDataSql(), query.getDataArgs(), context);
        return rows.stream().map(row -> reflectiveMapper.mapRow(row, viewType)).collect(Collectors.toList());
    }

    private <R> R executeSingle(CompiledQuery query, Class<R> viewType, RowCardinalityPolicy policy) {
        List<R> rows = executeMainRows(query, viewType);
        if (rows.size() > 1) {
            throw new QueryNotUniqueException("单条查询命中多条记录");
        }
        if (rows.isEmpty()) {
            if (policy == RowCardinalityPolicy.AT_MOST_ONE) {
                return null;
            }
            throw new NotFoundException("明细不存在");
        }
        List<R> single = new ArrayList<R>(1);
        single.add(rows.get(0));
        expandRelations(query, single);
        return single.get(0);
    }

    /**
     * 扩展查询结果中的关联数据。
     */
    private <R> void expandRelations(CompiledQuery query, List<R> roots) {
        if (roots.isEmpty() || query.getExpandEdges() == null || query.getExpandEdges().isEmpty()) {
            return;
        }
        Class<?> rootType = query.getQueryPlan().getSpec().getRootType();

        for (RelationEdge edge : query.getExpandEdges()) {
            boolean parentIsRoot = Objects.equals(edge.getFromEntity(), rootType);
            List<Object> parents = parentIsRoot
                ? new ArrayList<Object>(roots)
                : reflectiveMapper.collectObjectsOfType(roots, edge.getFromEntity());
            if (parents == null || parents.isEmpty()) {
                continue;
            }
            List<Object> parentIds = reflectiveMapper.extractFieldValues(parents, edge.getFromField());
            if (parentIds.isEmpty()) {
                continue;
            }
            if (edge.getScope() != RelationScope.LOCAL_DB) {
                List<Object> children = loadByRelationLoader(query, edge, parents, parentIds);
                assignLoadedChildren(roots, parents, parentIsRoot, edge, children);
                continue;
            }

            EntityMeta childMeta = metaRegistry.getEntityMeta(edge.getToEntity());
            String placeholders = parentIds.stream().map(v -> "?").collect(Collectors.joining(","));
            String toColumn = childMeta.resolveColumn(edge.getToField());
            StringBuilder sql = new StringBuilder("select ").append(buildExpandSelectClause(childMeta))
                .append(" from ").append(childMeta.getTable())
                .append(" c where c.").append(toColumn).append(" in (").append(placeholders).append(")");
            if (childMeta.getLogicDeleteField() != null && !childMeta.getLogicDeleteField().trim().isEmpty()) {
                sql.append(" and c.").append(childMeta.resolveColumn(childMeta.getLogicDeleteField())).append(" = 0");
            }

            DefaultExecutionContext context = buildContext(query, "expand");
            List<Map<String, Object>> childRows = guardedSqlExecutor.queryForList(sql.toString(), parentIds, context);
            List<Object> children = childRows.stream()
                .map(row -> reflectiveMapper.mapRow(row, edge.getToEntity()))
                .collect(Collectors.toList());

            assignLoadedChildren(roots, parents, parentIsRoot, edge, children);
        }
    }

    private List<Object> loadByRelationLoader(
        CompiledQuery query,
        RelationEdge edge,
        List<Object> parents,
        List<Object> parentIds
    ) {
        RelationLoader loader = relationLoaderRegistry.resolve(edge);
        if (loader == null) {
            throw new ValidationException("未找到可处理关系的 RelationLoader: " + edge.getRelationField());
        }
        List<Object> children = loader.load(new RelationLoadRequest(
            query.getQueryPlan().getSpec(),
            edge,
            parents,
            parentIds
        ));
        return children == null ? Collections.<Object>emptyList() : children;
    }

    private <R> void assignLoadedChildren(
        List<R> roots,
        List<Object> parents,
        boolean parentIsRoot,
        RelationEdge edge,
        List<Object> children
    ) {
        Map<Object, List<Object>> grouped = new HashMap<Object, List<Object>>();
        for (Object child : children) {
            Object parentId = reflectiveMapper.readField(child, edge.getToField());
            if (parentId == null) {
                continue;
            }
            List<Object> group = grouped.get(parentId);
            if (group == null) {
                group = new ArrayList<Object>();
                grouped.put(parentId, group);
            }
            group.add(child);
        }
        for (int i = 0; i < parents.size(); i++) {
            Object parent = parents.get(i);
            Object rootId = reflectiveMapper.readField(parent, edge.getFromField());
            Object updated = reflectiveMapper.assignChildren(
                parent,
                edge,
                grouped.containsKey(rootId) ? grouped.get(rootId) : Collections.<Object>emptyList()
            );
            if (updated != parent) {
                parents.set(i, updated);
                if (parentIsRoot) {
                    roots.set(i, castRoot(updated));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <R> R castRoot(Object source) {
        return (R) source;
    }

    private DefaultExecutionContext buildContext(CompiledQuery query, String phase) {
        String routeKey = RouteKeyFactory.buildQueryRouteKey(query.getQueryPlan().getSpec());
        DefaultExecutionContext context = new DefaultExecutionContext(routeKey, query.getQueryPlan().getSpec().getScene());
        context.getAttributes().put("operationDomain", query.getQueryPlan().getOp().domain().name());
        context.getAttributes().put("operation", query.getQueryPlan().getOp().name());
        context.getAttributes().put("phase", phase);
        return context;
    }

    private String buildExpandSelectClause(EntityMeta childMeta) {
        return childMeta.getAllowedFields().stream()
            .map(childMeta::resolveColumn)
            .filter(Objects::nonNull)
            .map(column -> "c." + column)
            .collect(Collectors.joining(","));
    }

    private boolean isPageOutOfRange(int page, int limit, long total) {
        if (page <= 1 || limit <= 0) {
            return false;
        }
        long offset = (long) (page - 1) * limit;
        return offset >= total;
    }

    private enum RowCardinalityPolicy {
        AT_MOST_ONE,
        EXACTLY_ONE
    }
}

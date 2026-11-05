package com.entloom.crud.engine.jdbc.query;

import com.entloom.crud.api.enums.QueryOperation;
import com.entloom.crud.api.model.CrudRecord;
import com.entloom.crud.core.capability.query.spec.QuerySpec;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.QueryNotUniqueException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.policy.EntityPolicyRegistry;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.meta.RelationEdge;
import com.entloom.crud.core.runtime.meta.RelationGraph;
import com.entloom.crud.core.runtime.validation.SpecValidator;
import com.entloom.crud.core.security.SqlSecurityGuard;
import com.entloom.crud.enums.RelationScope;
import com.entloom.meta.enums.RelationCardinality;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 父资源显式授予的一级关联投影：先读取授权根记录，再按实际连接键批量加载。 */
final class JdbcAuthorizedRelationReader {
    private final EntityMetaRegistry entities;
    private final SqlSecurityGuard guard;
    private final JdbcQueryCompiler compiler;
    private final JdbcQueryExecutor executor;
    private final RootFirstQueryPlanner planner = new RootFirstQueryPlanner();

    JdbcAuthorizedRelationReader(EntityMetaRegistry entities, SqlSecurityGuard guard,
        JdbcQueryCompiler compiler, JdbcQueryExecutor executor) {
        this.entities = entities;
        this.guard = guard;
        this.compiler = compiler;
        this.executor = executor;
    }

    List<Projection> prepare(EntityMeta root, List<String> relations, AuthorizationFields fields) {
        List<Projection> projections = new ArrayList<Projection>();
        Set<String> seen = new LinkedHashSet<String>();
        for (String name : relations) {
            EntityFieldMeta relation = root.resolveFieldMeta(name);
            if (name == null || (relation != null && !relation.isRelation()) || !seen.add(name)
                || !EntityPolicyRegistry.allowsFieldUse(root.getEntityType(), name, AuthorizationFields.Use.EXPAND)) {
                throw new PermissionDeniedException("关联展开必须使用唯一的已声明关系: " + name);
            }
            RelationEdge edge = null;
            for (RelationEdge candidate : entities.getRelationGraph(root.getEntityType()).outgoingOf(root.getEntityType())) {
                if (name.equals(candidate.getRelationField())) {
                    if (edge != null) throw new PermissionDeniedException("关联展开定义不唯一: " + name);
                    edge = candidate;
                }
            }
            if (edge == null || edge.getScope() != RelationScope.LOCAL_DB
                || (edge.getCardinality() != RelationCardinality.ONE_TO_MANY
                    && edge.getCardinality() != RelationCardinality.MANY_TO_ONE
                    && edge.getCardinality() != RelationCardinality.ONE_TO_ONE)) {
                throw new PermissionDeniedException("受授权关联只支持本地一级关系: " + name);
            }
            EntityMeta target = entities.getEntityMeta(edge.getToEntity());
            if (target == null || root.resolveColumn(edge.getFromField()) == null
                || target.resolveColumn(edge.getToField()) == null) {
                throw new PermissionDeniedException("关联连接字段未注册: " + name);
            }
            List<String> returned = new ArrayList<String>();
            for (EntityFieldMeta field : target.getFieldMetas().values()) {
                if (!field.isRelation() && field.getColumnName() != null
                    && EntityPolicyRegistry.allowsFieldUse(target.getEntityType(), field.getFieldName(), AuthorizationFields.Use.EXPAND)
                    && (fields.allows(AuthorizationFields.Use.EXPAND, name)
                        || fields.allows(AuthorizationFields.Use.EXPAND, name + "." + field.getFieldName()))) {
                    returned.add(field.getFieldName());
                }
            }
            if (returned.isEmpty()) throw new PermissionDeniedException("本次查询不允许关联展开: " + name);
            projections.add(new Projection(edge, target, returned));
        }
        return projections;
    }

    List<CrudRecord> expand(List<CrudRecord> rows, List<String> returned, List<Projection> projections) {
        List<Map<String, Object>> output = new ArrayList<Map<String, Object>>();
        for (CrudRecord row : rows) output.add(new LinkedHashMap<String, Object>(
            JdbcAuthorizedQueryExecutor.project(Collections.singletonList(row), returned).get(0).asMap()));
        for (Projection projection : projections) {
            Set<Object> keys = new LinkedHashSet<Object>();
            for (CrudRecord row : rows) {
                Object key = value(row, projection.edge.getFromField());
                if (key != null) keys.add(key);
            }
            Map<Object, List<CrudRecord>> grouped = load(projection, new ArrayList<Object>(keys));
            for (int index = 0; index < rows.size(); index++) {
                List<CrudRecord> children = grouped.getOrDefault(value(rows.get(index), projection.edge.getFromField()),
                    Collections.<CrudRecord>emptyList());
                if (projection.edge.getCardinality() != RelationCardinality.ONE_TO_MANY && children.size() > 1) {
                    throw new QueryNotUniqueException("单值关联返回多条记录: " + projection.edge.getRelationField());
                }
                output.get(index).put(projection.edge.getRelationField(),
                    projection.edge.getCardinality() == RelationCardinality.ONE_TO_MANY
                        ? children : children.isEmpty() ? null : children.get(0));
            }
        }
        List<CrudRecord> result = new ArrayList<CrudRecord>();
        for (Map<String, Object> row : output) result.add(CrudRecord.copyOf(row));
        return result;
    }

    private Map<Object, List<CrudRecord>> load(Projection projection, List<Object> keys) {
        Map<Object, List<CrudRecord>> grouped = new LinkedHashMap<Object, List<CrudRecord>>();
        Set<String> selected = new LinkedHashSet<String>(projection.returned);
        selected.add(projection.edge.getToField());
        int loaded = 0;
        // 分批限制 IN 参数；多读一条检测超限，绝不静默截断明细。
        for (int offset = 0; offset < keys.size(); offset += 200) {
            List<Object> batch = keys.subList(offset, Math.min(offset + 200, keys.size()));
            QuerySpec<CrudRecord> spec = QuerySpec.<CrudRecord>builder().rootType(projection.target.getEntityType())
                .resultType(CrudRecord.class).op(QueryOperation.LIST).selectFields(new ArrayList<String>(selected))
                .limit(SpecValidator.DEFAULT_MAX_LIMIT + 1).build();
            guard.beforeCompile(spec);
            List<CrudRecord> children = executor.executeList(compiler.compileAuthorized(
                planner.plan(spec, projection.target, RelationGraph.empty()),
                AuthorizationScope.in(projection.edge.getToField(), batch)), CrudRecord.class);
            loaded += children.size();
            if (loaded > SpecValidator.DEFAULT_MAX_LIMIT) {
                throw new ValidationException("单次关联展开条数超过最大限制 " + SpecValidator.DEFAULT_MAX_LIMIT);
            }
            for (CrudRecord child : children) {
                grouped.computeIfAbsent(value(child, projection.edge.getToField()), key -> new ArrayList<CrudRecord>())
                    .add(JdbcAuthorizedQueryExecutor.project(Collections.singletonList(child), projection.returned).get(0));
            }
        }
        return grouped;
    }

    private static Object value(CrudRecord row, String field) {
        return JdbcAuthorizedQueryExecutor.project(Collections.singletonList(row), Collections.singletonList(field))
            .get(0).raw(field);
    }

    static final class Projection {
        final RelationEdge edge;
        final EntityMeta target;
        final List<String> returned;

        Projection(RelationEdge edge, EntityMeta target, List<String> returned) {
            this.edge = edge;
            this.target = target;
            this.returned = returned;
        }
    }
}

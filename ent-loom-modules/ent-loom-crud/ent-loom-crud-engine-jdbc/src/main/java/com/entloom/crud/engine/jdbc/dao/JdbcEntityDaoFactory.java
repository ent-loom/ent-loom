package com.entloom.crud.engine.jdbc.dao;

import com.entloom.crud.core.capability.dao.EntityAccessScope;
import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.core.capability.dao.EntityDaoFactory;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.capability.dao.RowConstraint;
import com.entloom.crud.core.capability.dao.RowConstraintNormalizer;
import com.entloom.crud.core.capability.dao.RowConstraintOperator;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.security.GuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.dialect.JdbcDialect;
import com.entloom.crud.engine.jdbc.dialect.StandardJdbcDialect;

/**
 * JDBC 实体 DAO 工厂。
 */
public final class JdbcEntityDaoFactory implements EntityDaoFactory {
    private final EntityMetaRegistry metaRegistry;
    private final GuardedSqlExecutor guardedSqlExecutor;
    private final int maxParameters;
    private final JdbcDialect dialect;

    public JdbcEntityDaoFactory(EntityMetaRegistry metaRegistry, GuardedSqlExecutor guardedSqlExecutor) {
        this(
            metaRegistry,
            guardedSqlExecutor,
            StandardJdbcDialect.GENERIC,
            JdbcEntityPredicateCompiler.DEFAULT_MAX_PARAMETERS
        );
    }

    public JdbcEntityDaoFactory(
        EntityMetaRegistry metaRegistry,
        GuardedSqlExecutor guardedSqlExecutor,
        int maxParameters
    ) {
        this(metaRegistry, guardedSqlExecutor, StandardJdbcDialect.GENERIC, maxParameters);
    }

    public JdbcEntityDaoFactory(
        EntityMetaRegistry metaRegistry,
        GuardedSqlExecutor guardedSqlExecutor,
        JdbcDialect dialect
    ) {
        this(metaRegistry, guardedSqlExecutor, dialect, JdbcEntityPredicateCompiler.DEFAULT_MAX_PARAMETERS);
    }

    public JdbcEntityDaoFactory(
        EntityMetaRegistry metaRegistry,
        GuardedSqlExecutor guardedSqlExecutor,
        JdbcDialect dialect,
        int maxParameters
    ) {
        if (metaRegistry == null || guardedSqlExecutor == null) {
            throw new ValidationException("EntityMetaRegistry 和 GuardedSqlExecutor 不能为空");
        }
        if (maxParameters <= 0) {
            throw new ValidationException("DAO SQL 参数上限必须大于 0");
        }
        this.metaRegistry = metaRegistry;
        this.guardedSqlExecutor = guardedSqlExecutor;
        this.dialect = dialect == null ? StandardJdbcDialect.GENERIC : dialect;
        this.maxParameters = maxParameters;
    }

    @Override
    public <T, ID> EntityDao<T, ID> scoped(EntityType<T, ID> entityType, EntityAccessScope scope) {
        if (entityType == null || scope == null) {
            throw new ValidationException("EntityType 和 EntityAccessScope 不能为空");
        }
        EntityMeta meta = metaRegistry.getEntityMeta(entityType.getEntityClass());
        validateEntityType(entityType, meta);
        RowConstraint normalizedScope = RowConstraintNormalizer.normalize(scope.getRowConstraint(), meta);
        validateScope(normalizedScope, meta);
        return new JdbcEntityDao<T, ID>(
            entityType,
            meta,
            normalizedScope,
            guardedSqlExecutor,
            new JdbcEntityPredicateCompiler(dialect, maxParameters),
            dialect
        );
    }

    private <T, ID> void validateEntityType(EntityType<T, ID> entityType, EntityMeta meta) {
        if (meta.getEntityType() == null || !meta.getEntityType().equals(entityType.getEntityClass())) {
            throw new ValidationException("EntityType 实体类型与元数据不一致");
        }
        if (meta.getIdPolicy() != EntityIdPolicy.EXPLICIT) {
            throw new ValidationException("首期 EntityDao 只支持显式主键: " + meta.getEntityName());
        }
        EntityFieldMeta idField = meta.resolveFieldMeta(meta.getIdField());
        if (idField == null || !wrap(idField.getJavaType()).equals(wrap(entityType.getIdClass()))) {
            throw new ValidationException("EntityType 主键类型与元数据不一致: " + meta.getEntityName());
        }
        if (meta.getLogicDeleteField() != null && !meta.getLogicDeleteField().trim().isEmpty()
            && !meta.hasExplicitLogicDeleteValues()) {
            throw new ValidationException("逻辑删除值未显式配置: " + meta.getEntityName());
        }
    }

    private void validateScope(RowConstraint constraint, EntityMeta meta) {
        switch (constraint.getKind()) {
            case UNRESTRICTED:
                return;
            case AND:
                for (RowConstraint child : constraint.getChildren()) {
                    validateScope(child, meta);
                }
                return;
            case PREDICATE:
                EntityFieldMeta field = meta.resolveFieldMeta(constraint.getField());
                if (field == null || !field.isScopeField()) {
                    throw new ValidationException("DAO 范围字段必须声明为 scopeField: " + constraint.getField());
                }
                RowConstraintOperator operator = constraint.getOperator();
                if (operator != RowConstraintOperator.EQ && operator != RowConstraintOperator.IN) {
                    throw new ValidationException("DAO 首期范围只支持等值和 IN: " + operator.getDisplayName());
                }
                for (Object value : constraint.getValues()) {
                    if (value == null) {
                        throw new ValidationException("DAO 范围值不能为 NULL: " + constraint.getField());
                    }
                }
                return;
            case OR:
            case NOT:
            default:
                throw new ValidationException("DAO 首期范围不支持该表达式: " + constraint.getKind().getDisplayName());
        }
    }

    private Class<?> wrap(Class<?> type) {
        if (type == null || !type.isPrimitive()) {
            return type;
        }
        if (type == Long.TYPE) {
            return Long.class;
        }
        if (type == Integer.TYPE) {
            return Integer.class;
        }
        if (type == Short.TYPE) {
            return Short.class;
        }
        if (type == Byte.TYPE) {
            return Byte.class;
        }
        if (type == Boolean.TYPE) {
            return Boolean.class;
        }
        return type;
    }
}

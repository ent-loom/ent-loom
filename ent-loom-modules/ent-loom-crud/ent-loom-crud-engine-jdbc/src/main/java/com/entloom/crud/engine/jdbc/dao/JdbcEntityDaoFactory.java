package com.entloom.crud.engine.jdbc.dao;

import com.entloom.crud.core.capability.dao.EntityAccessScope;
import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.core.capability.dao.EntityDaoFactory;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.capability.dao.RowConstraint;
import com.entloom.crud.core.capability.dao.RowConstraintNormalizer;
import com.entloom.crud.core.capability.dao.RowConstraintOperator;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionExecutor;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.security.GuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.dialect.JdbcDialect;
import com.entloom.crud.engine.jdbc.dialect.StandardJdbcDialect;
import com.entloom.crud.engine.jdbc.security.JdbcGuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.security.JdbcInsertScopeDatabaseValidator;
import com.entloom.crud.engine.jdbc.transaction.JdbcCrudWriteTransactionExecutor;
import java.lang.reflect.Method;

/**
 * JDBC 实体 DAO 工厂。
 */
public final class JdbcEntityDaoFactory implements EntityDaoFactory {
    /** JDBC DAO 和 Command 批量写入的默认数量上限。 */
    public static final int DEFAULT_MAX_BATCH_SIZE = 500;
    private final EntityMetaRegistry metaRegistry;
    private final GuardedSqlExecutor guardedSqlExecutor;
    private final int maxParameters;
    private final JdbcDialect dialect;
    private final JdbcInsertScopeDatabaseValidator insertScopeDatabaseValidator;
    private final CrudWriteTransactionExecutor transactionExecutor;
    private final int maxBatchSize;

    public JdbcEntityDaoFactory(EntityMetaRegistry metaRegistry, GuardedSqlExecutor guardedSqlExecutor) {
        this(
            metaRegistry,
            guardedSqlExecutor,
            StandardJdbcDialect.GENERIC,
            JdbcEntityPredicateCompiler.DEFAULT_MAX_PARAMETERS,
            null
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
        this(metaRegistry, guardedSqlExecutor, dialect, maxParameters, null);
    }

    /**
     * 创建使用默认参数上限并绑定数据库结构安全校验器的 Factory。
     */
    public JdbcEntityDaoFactory(
        EntityMetaRegistry metaRegistry,
        GuardedSqlExecutor guardedSqlExecutor,
        JdbcDialect dialect,
        JdbcInsertScopeDatabaseValidator insertScopeDatabaseValidator
    ) {
        this(
            metaRegistry,
            guardedSqlExecutor,
            dialect,
            JdbcEntityPredicateCompiler.DEFAULT_MAX_PARAMETERS,
            insertScopeDatabaseValidator
        );
    }

    /** 创建使用默认参数和批量上限、可加入 Spring 事务的 Factory。 */
    public JdbcEntityDaoFactory(
        EntityMetaRegistry metaRegistry,
        GuardedSqlExecutor guardedSqlExecutor,
        JdbcDialect dialect,
        JdbcInsertScopeDatabaseValidator insertScopeDatabaseValidator,
        CrudWriteTransactionExecutor transactionExecutor
    ) {
        this(
            metaRegistry,
            guardedSqlExecutor,
            dialect,
            JdbcEntityPredicateCompiler.DEFAULT_MAX_PARAMETERS,
            insertScopeDatabaseValidator,
            transactionExecutor,
            DEFAULT_MAX_BATCH_SIZE
        );
    }

    /**
     * 创建 JDBC DAO Factory，并绑定数据库结构安全校验器。
     *
     * <p>自定义 {@link GuardedSqlExecutor} 时必须显式传入校验器；标准
     * {@link JdbcGuardedSqlExecutor} 会自动从其底层 {@code DataSource} 创建校验器。</p>
     */
    public JdbcEntityDaoFactory(
        EntityMetaRegistry metaRegistry,
        GuardedSqlExecutor guardedSqlExecutor,
        JdbcDialect dialect,
        int maxParameters,
        JdbcInsertScopeDatabaseValidator insertScopeDatabaseValidator
    ) {
        this(
            metaRegistry,
            guardedSqlExecutor,
            dialect,
            maxParameters,
            insertScopeDatabaseValidator,
            null,
            DEFAULT_MAX_BATCH_SIZE
        );
    }

    /**
     * 创建带批量事务执行器的 JDBC DAO 工厂。
     *
     * <p>未显式传入事务执行器时，标准 JDBC 执行器会自动绑定其数据源；自定义执行器需显式提供事务能力。</p>
     */
    public JdbcEntityDaoFactory(
        EntityMetaRegistry metaRegistry,
        GuardedSqlExecutor guardedSqlExecutor,
        JdbcDialect dialect,
        int maxParameters,
        JdbcInsertScopeDatabaseValidator insertScopeDatabaseValidator,
        CrudWriteTransactionExecutor transactionExecutor,
        int maxBatchSize
    ) {
        if (metaRegistry == null || guardedSqlExecutor == null) {
            throw new ValidationException("EntityMetaRegistry 和 GuardedSqlExecutor 不能为空");
        }
        if (maxParameters <= 0) {
            throw new ValidationException("DAO SQL 参数上限必须大于 0");
        }
        if (maxBatchSize <= 0) {
            throw new ValidationException("DAO 批量数量上限必须大于 0");
        }
        this.metaRegistry = metaRegistry;
        this.guardedSqlExecutor = guardedSqlExecutor;
        this.dialect = dialect == null ? StandardJdbcDialect.GENERIC : dialect;
        this.maxParameters = maxParameters;
        this.insertScopeDatabaseValidator = insertScopeDatabaseValidator == null
            ? resolveInsertScopeValidator(metaRegistry, guardedSqlExecutor)
            : insertScopeDatabaseValidator;
        this.transactionExecutor = transactionExecutor == null
            ? resolveTransactionExecutor(guardedSqlExecutor)
            : transactionExecutor;
        this.maxBatchSize = maxBatchSize;
    }

    @Override
    public <T, ID> EntityDao<T, ID> scoped(EntityType<T, ID> entityType, EntityAccessScope scope) {
        ScopedEntity scoped = resolveScopedEntity(entityType, scope);
        return new JdbcEntityDao<T, ID>(
            entityType,
            scoped.meta,
            scoped.scope,
            guardedSqlExecutor,
            new JdbcEntityPredicateCompiler(dialect, maxParameters),
            dialect,
            transactionExecutor,
            maxBatchSize
        );
    }

    /**
     * 返回当前 Factory 使用的批量事务执行器，供同一 JDBC Command 入口复用。
     *
     * @return 事务执行器；自定义且无法识别数据源的执行器可能为空
     */
    public CrudWriteTransactionExecutor getTransactionExecutor() {
        return transactionExecutor;
    }

    @Override
    public void validateCustomMethod(EntityType<?, ?> entityType, Method method) {
        if (entityType == null || method == null) {
            throw new ValidationException("DAO 自定义方法的实体类型和方法不能为空");
        }
        EntityMeta meta = metaRegistry.getEntityMeta(entityType.getEntityClass());
        validateEntityType(entityType, meta);
        validateDatabaseStructure(meta);
        JdbcEntityDaoCustomMethodExecutor.validate(method, meta, dialect, maxParameters);
    }

    @Override
    public Object invokeCustom(
        EntityType<?, ?> entityType,
        EntityAccessScope scope,
        Method method,
        Object[] args
    ) {
        ScopedEntity scoped = resolveScopedEntity(entityType, scope);
        return new JdbcEntityDaoCustomMethodExecutor(
            scoped.meta,
            scoped.scope,
            guardedSqlExecutor,
            dialect,
            maxParameters
        ).invoke(method, args);
    }

    private ScopedEntity resolveScopedEntity(EntityType<?, ?> entityType, EntityAccessScope scope) {
        if (entityType == null || scope == null) {
            throw new ValidationException("EntityType 和 EntityAccessScope 不能为空");
        }
        EntityMeta meta = metaRegistry.getEntityMeta(entityType.getEntityClass());
        validateEntityType(entityType, meta);
        validateDatabaseStructure(meta);
        RowConstraint normalizedScope = RowConstraintNormalizer.normalize(scope.getRowConstraint(), meta);
        validateScope(normalizedScope, meta);
        return new ScopedEntity(meta, normalizedScope);
    }

    private static final class ScopedEntity {
        private final EntityMeta meta;
        private final RowConstraint scope;

        private ScopedEntity(EntityMeta meta, RowConstraint scope) {
            this.meta = meta;
            this.scope = scope;
        }
    }

    private void validateDatabaseStructure(EntityMeta meta) {
        if (!hasScopeField(meta)) {
            return;
        }
        if (insertScopeDatabaseValidator == null) {
            throw new ValidationException(
                "包含范围字段的 JDBC DAO 必须配置 JdbcInsertScopeDatabaseValidator: "
                    + meta.getEntityName()
            );
        }
        insertScopeDatabaseValidator.validateEntityOrThrow(meta);
    }

    private boolean hasScopeField(EntityMeta meta) {
        if (meta == null || meta.getFieldMetas() == null) {
            return false;
        }
        for (EntityFieldMeta field : meta.getFieldMetas().values()) {
            if (field != null && field.isScopeField()) {
                return true;
            }
        }
        return false;
    }

    private JdbcInsertScopeDatabaseValidator resolveInsertScopeValidator(
        EntityMetaRegistry registry,
        GuardedSqlExecutor executor
    ) {
        if (!(executor instanceof JdbcGuardedSqlExecutor)) {
            return null;
        }
        javax.sql.DataSource dataSource = ((JdbcGuardedSqlExecutor) executor).getDataSource();
        return dataSource == null ? null : new JdbcInsertScopeDatabaseValidator(dataSource, registry);
    }

    private CrudWriteTransactionExecutor resolveTransactionExecutor(GuardedSqlExecutor executor) {
        if (!(executor instanceof JdbcGuardedSqlExecutor)) {
            return null;
        }
        javax.sql.DataSource dataSource = ((JdbcGuardedSqlExecutor) executor).getDataSource();
        return dataSource == null ? null : new JdbcCrudWriteTransactionExecutor(dataSource);
    }

    private <T, ID> void validateEntityType(EntityType<T, ID> entityType, EntityMeta meta) {
        if (meta.getEntityType() == null || !meta.getEntityType().equals(entityType.getEntityClass())) {
            throw new ValidationException("EntityType 实体类型与元数据不一致");
        }
        if (meta.getIdPolicy() != EntityIdPolicy.EXPLICIT && meta.getIdPolicy() != EntityIdPolicy.GENERATED) {
            throw new ValidationException("EntityDao 不支持该主键策略: " + meta.getIdPolicy()
                + ": " + meta.getEntityName());
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

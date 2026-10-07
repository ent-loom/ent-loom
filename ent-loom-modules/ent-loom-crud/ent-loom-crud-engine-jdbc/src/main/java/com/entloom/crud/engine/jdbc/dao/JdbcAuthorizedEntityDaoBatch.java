package com.entloom.crud.engine.jdbc.dao;

import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.core.capability.dao.EntityDaoAuthorizationContext;
import com.entloom.crud.core.capability.dao.EntityDaoBatch;
import com.entloom.crud.core.capability.dao.EntityDaoBatchAuthorizationContext;
import com.entloom.crud.core.capability.dao.EntityDaoBatchAtomicity;
import com.entloom.crud.core.capability.dao.EntityDaoBatchExecutionResult;
import com.entloom.crud.core.capability.dao.EntityDaoBatchReadResult;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionExecutor;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.security.GuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.dialect.JdbcDialect;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** JDBC 逐目标授权只读批量 DAO。 */
final class JdbcAuthorizedEntityDaoBatch<T, ID> implements EntityDaoBatch<T, ID> {
    private final List<String> targetIds;
    private final Map<String, EntityDao<T, ID>> delegates;
    private final EntityDaoBatchAuthorizationContext authorization;

    JdbcAuthorizedEntityDaoBatch(
        EntityType<T, ID> entityType,
        EntityMeta meta,
        EntityDaoBatchAuthorizationContext authorization,
        GuardedSqlExecutor executor,
        JdbcDialect dialect,
        CrudGovernanceAuditRecorder audit,
        CrudWriteTransactionExecutor transactionExecutor
    ) {
        Objects.requireNonNull(entityType, "DAO 实体类型不能为空");
        Objects.requireNonNull(meta, "DAO 实体元数据不能为空");
        Objects.requireNonNull(authorization, "批量 DAO 授权上下文不能为空");
        Objects.requireNonNull(executor, "SQL 执行器不能为空");
        Objects.requireNonNull(dialect, "JDBC 方言不能为空");
        Objects.requireNonNull(audit, "治理审计记录器不能为空");
        this.authorization = authorization;
        LinkedHashMap<String, EntityDao<T, ID>> created = new LinkedHashMap<String, EntityDao<T, ID>>();
        for (EntityDaoAuthorizationContext context : authorization.getAuthorizations()) {
            created.put(
                context.getTargetId(),
                new JdbcAuthorizedEntityDao<T, ID>(
                    entityType, meta, context, executor, dialect, audit, transactionExecutor
                )
            );
        }
        this.targetIds = Collections.unmodifiableList(new ArrayList<String>(authorization.getTargetIds()));
        this.delegates = Collections.unmodifiableMap(created);
    }

    @Override
    public List<T> findAllById(Collection<ID> ids) {
        return new ArrayList<T>(findAllByIdAsMap(ids).values());
    }

    @Override
    public Map<ID, T> findAllByIdAsMap(Collection<ID> ids) {
        if (ids == null) {
            throw new ValidationException("批量 DAO 主键集合不能为空");
        }
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashMap<ID, EntityDao<T, ID>> requested = new LinkedHashMap<ID, EntityDao<T, ID>>();
        for (ID id : ids) {
            if (id == null) {
                throw new ValidationException("批量 DAO 主键不能为 NULL");
            }
            String targetId = String.valueOf(id);
            EntityDao<T, ID> delegate = delegates.get(targetId);
            if (delegate == null) {
                throw new PermissionDeniedException("批量 DAO 主键不属于已授权目标: " + targetId);
            }
            if (!requested.containsKey(id)) {
                requested.put(id, delegate);
            }
        }
        LinkedHashMap<ID, T> result = new LinkedHashMap<ID, T>();
        for (Map.Entry<ID, EntityDao<T, ID>> entry : requested.entrySet()) {
            Optional<T> value = entry.getValue().findById(entry.getKey());
            if (value.isPresent()) {
                result.put(entry.getKey(), value.get());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public EntityDaoBatchReadResult<T, ID> readById(Collection<ID> ids) {
        if (ids == null || ids.size() != targetIds.size()) {
            throw new ValidationException("批量读取主键必须逐项对应授权目标");
        }
        LinkedHashMap<String, ID> idsByTarget = new LinkedHashMap<String, ID>();
        for (ID id : ids) {
            if (id == null) {
                throw new ValidationException("批量读取主键不能为 NULL");
            }
            String targetId = String.valueOf(id);
            if (!delegates.containsKey(targetId) || idsByTarget.put(targetId, id) != null) {
                throw new PermissionDeniedException("批量读取主键不能偏离授权目标: " + targetId);
            }
        }
        if (idsByTarget.size() != targetIds.size()) {
            throw new PermissionDeniedException("批量读取主键必须覆盖全部授权目标");
        }
        List<EntityDaoBatchExecutionResult.Item> items = new ArrayList<EntityDaoBatchExecutionResult.Item>();
        LinkedHashMap<ID, T> values = new LinkedHashMap<ID, T>();
        boolean hasSuccess = false;
        boolean hasFailure = false;
        for (String targetId : targetIds) {
            ID id = idsByTarget.get(targetId);
            try {
                Optional<T> value = delegates.get(targetId).findById(id);
                if (value.isPresent()) {
                    values.put(id, value.get());
                    items.add(EntityDaoBatchExecutionResult.Item.succeeded(targetId));
                    hasSuccess = true;
                } else {
                    items.add(EntityDaoBatchExecutionResult.Item.noData(targetId));
                }
            } catch (RuntimeException | Error failure) {
                items.add(EntityDaoBatchExecutionResult.Item.executionFailed(targetId));
                hasFailure = true;
            }
        }
        EntityDaoBatchAtomicity atomicity = hasSuccess && hasFailure
            ? EntityDaoBatchAtomicity.EXPLICIT_PARTIAL
            : EntityDaoBatchAtomicity.ALL_OR_NOTHING;
        return EntityDaoBatchReadResult.of(
            EntityDaoBatchExecutionResult.of(authorization, items, atomicity), values
        );
    }

    @Override
    public List<String> getTargetIds() {
        return targetIds;
    }
}

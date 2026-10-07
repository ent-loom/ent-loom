package com.entloom.crud.engine.jdbc.dao;

import com.entloom.crud.core.capability.command.patch.UpdatePatch;
import com.entloom.crud.core.capability.command.patch.NormalizedUpdatePatch;
import com.entloom.crud.core.capability.dao.EntityDao;
import com.entloom.crud.core.capability.dao.EntityDaoAuthorizationContext;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.exception.EntityDaoPersistenceException;
import com.entloom.crud.core.exception.EntityDaoWriteMissException;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionExecutor;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionPolicy;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.runtime.context.DefaultExecutionContext;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditEvent;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditOutcome;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditReasonCode;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.governance.admission.AccessAdmission;
import com.entloom.crud.core.governance.admission.AccessContextScope;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.security.GuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.dialect.JdbcDialect;
import com.entloom.crud.engine.jdbc.query.JdbcReflectiveMapper;
import com.entloom.crud.engine.jdbc.sql.JdbcAuthorizationScopeCompiler;
import com.entloom.crud.engine.jdbc.sql.JdbcLogicDeleteValues;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 按次授权的 JDBC DAO 最小实现。
 *
 * <p>当前只开放绑定单一主键目标的 {@link #findById(Object)}、局部更新和删除；批量及其他
 * DAO 形状在授权结果能力完整适配前统一拒绝。</p>
 */
final class JdbcAuthorizedEntityDao<T, ID> implements EntityDao<T, ID> {
    private final EntityType<T, ID> entityType;
    private final EntityMeta meta;
    private final EntityDaoAuthorizationContext authorization;
    private final GuardedSqlExecutor executor;
    private final JdbcDialect dialect;
    private final CrudGovernanceAuditRecorder audit;
    private final CrudWriteTransactionExecutor transactionExecutor;
    private final JdbcAuthorizationScopeCompiler scopes = new JdbcAuthorizationScopeCompiler();
    private volatile List<String> returnFields;

    JdbcAuthorizedEntityDao(
        EntityType<T, ID> entityType,
        EntityMeta meta,
        EntityDaoAuthorizationContext authorization,
        GuardedSqlExecutor executor,
        JdbcDialect dialect,
        CrudGovernanceAuditRecorder audit,
        CrudWriteTransactionExecutor transactionExecutor
    ) {
        this.entityType = Objects.requireNonNull(entityType, "DAO 实体类型不能为空");
        this.meta = Objects.requireNonNull(meta, "DAO 实体元数据不能为空");
        this.authorization = Objects.requireNonNull(authorization, "DAO 授权上下文不能为空");
        this.executor = Objects.requireNonNull(executor, "SQL 执行器不能为空");
        this.dialect = Objects.requireNonNull(dialect, "JDBC 方言不能为空");
        this.audit = Objects.requireNonNull(audit, "治理审计记录器不能为空");
        this.transactionExecutor = transactionExecutor;
        authorization.requireResource(meta.getResourceDescriptor().getResourceCode());
    }

    @Override
    public Optional<T> findById(ID id) {
        long started = System.nanoTime();
        CrudErrorStage stage = CrudErrorStage.GOVERNANCE;
        try {
            Object normalizedId = normalizeTargetId(id);
            JdbcAuthorizationScopeCompiler.Predicate scope = scopes.compile(
                authorization.getScope(), meta, "t", dialect
            );
            List<String> predicates = new ArrayList<String>();
            List<Object> args = new ArrayList<Object>();
            predicates.add(scope.getSql());
            args.addAll(scope.getArgs());
            appendLogicDelete(predicates, args, "t");
            predicates.add("t." + quote(meta.resolveColumn(meta.getIdField())) + " = ?");
            args.add(normalizedId);

            String sql = "select " + selectColumns() + " from " + quote(meta.getTable()) + " t"
                + " where " + String.join(" and ", predicates);
            stage = CrudErrorStage.EXECUTE;
            List<Map<String, Object>> rows = executor.queryForList(sql, args, context());
            if (rows.isEmpty()) {
                record(CrudGovernanceAuditOutcome.SUCCESS, CrudGovernanceAuditReasonCode.NONE, started, null);
                return Optional.empty();
            }
            if (rows.size() > 1) {
                throw new EntityDaoPersistenceException("按次授权 DAO 按主键查询命中多行: " + meta.getEntityName());
            }
            Optional<T> value = Optional.ofNullable(new JdbcReflectiveMapper().mapRow(rows.get(0), entityType.getEntityClass()));
            record(CrudGovernanceAuditOutcome.SUCCESS, CrudGovernanceAuditReasonCode.NONE, started, null);
            return value;
        } catch (RuntimeException | Error failure) {
            CrudGovernanceAuditOutcome outcome = stage == CrudErrorStage.EXECUTE
                ? CrudGovernanceAuditOutcome.EXECUTION_FAILED
                : CrudGovernanceAuditOutcome.GOVERNANCE_DENIED;
            CrudGovernanceAuditReasonCode reason = failure instanceof CrudException
                ? CrudGovernanceAuditReasonCode.fromCrudErrorCode(((CrudException) failure).getErrorCode())
                : CrudGovernanceAuditReasonCode.EXECUTION_FAILED;
            try {
                record(outcome, reason, started, stage);
            } catch (RuntimeException | Error auditFailure) {
                if (auditFailure != failure) {
                    failure.addSuppressed(auditFailure);
                }
            }
            throw failure;
        }
    }

    @Override
    public List<T> findAllById(Collection<ID> ids) {
        throw unsupported("findAllById", false);
    }

    @Override
    public Map<ID, T> findAllByIdAsMap(Collection<ID> ids) {
        throw unsupported("findAllByIdAsMap", false);
    }

    @Override
    public ID insert(T entity) {
        throw unsupported("insert", true);
    }

    @Override
    public List<ID> insertAll(Collection<T> entities) {
        throw unsupported("insertAll", true);
    }

    @Override
    public int update(T entity) {
        throw unsupported("update", true);
    }

    @Override
    public int updateById(T entity) {
        throw unsupported("updateById", true);
    }

    @Override
    public int updateById(ID id, UpdatePatch<T> patch) {
        long started = System.nanoTime();
        CrudErrorStage stage = CrudErrorStage.GOVERNANCE;
        try {
            requireOperation("update");
            Object normalizedId = normalizeTargetId(id);
            NormalizedUpdatePatch normalizedPatch = NormalizedUpdatePatch.from(patch, meta);
            Object patchId = JdbcEntityValueBinder.normalize(
                meta.resolveFieldMeta(meta.getIdField()), normalizedPatch.getId()
            );
            if (!Objects.equals(normalizedId, patchId)) {
                throw new ValidationException("Patch 主键与 updateById 参数不一致");
            }
            Long expectedVersion = expectedPatchVersion(patch);
            requireWriteFields(normalizedPatch.getChanges());
            requireTransactionExecutor();
            stage = CrudErrorStage.EXECUTE;
            final Object targetId = normalizedId;
            final Map<String, Object> changes = normalizedPatch.getChanges();
            final Long version = expectedVersion;
            int rows = transactionExecutor.execute(CrudWriteTransactionPolicy.SINGLE_TRANSACTION,
                () -> executeUpdate(targetId, changes, version, patch.getEntity()));
            recordCommand("updateById", CrudGovernanceAuditOutcome.SUCCESS,
                CrudGovernanceAuditReasonCode.NONE, started, null);
            return rows;
        } catch (RuntimeException | Error failure) {
            recordFailure("updateById", failure, started, stage);
            throw failure;
        }
    }

    @Override
    public int updateAll(Collection<T> entities) {
        throw unsupported("updateAll", true);
    }

    @Override
    public int delete(T entity) {
        throw unsupported("delete", true);
    }

    @Override
    public int deleteById(ID id) {
        long started = System.nanoTime();
        CrudErrorStage stage = CrudErrorStage.GOVERNANCE;
        try {
            requireOperation("delete");
            Object normalizedId = normalizeTargetId(id);
            if (hasVersion()) {
                throw new ValidationException("启用 version 的实体必须携带预期版本删除: " + meta.getEntityName());
            }
            requireTransactionExecutor();
            stage = CrudErrorStage.EXECUTE;
            int rows = transactionExecutor.execute(CrudWriteTransactionPolicy.SINGLE_TRANSACTION,
                () -> executeDelete(normalizedId, null));
            recordCommand("deleteById", CrudGovernanceAuditOutcome.SUCCESS,
                CrudGovernanceAuditReasonCode.NONE, started, null);
            return rows;
        } catch (RuntimeException | Error failure) {
            recordFailure("deleteById", failure, started, stage);
            throw failure;
        }
    }

    @Override
    public int deleteById(ID id, long expectedVersion) {
        long started = System.nanoTime();
        CrudErrorStage stage = CrudErrorStage.GOVERNANCE;
        try {
            requireOperation("delete");
            Object normalizedId = normalizeTargetId(id);
            requireVersionSupport();
            requireTransactionExecutor();
            stage = CrudErrorStage.EXECUTE;
            final Object targetId = normalizedId;
            final Object version = normalizeVersion(Long.valueOf(expectedVersion));
            int rows = transactionExecutor.execute(CrudWriteTransactionPolicy.SINGLE_TRANSACTION,
                () -> executeDelete(targetId, version));
            recordCommand("deleteById", CrudGovernanceAuditOutcome.SUCCESS,
                CrudGovernanceAuditReasonCode.NONE, started, null);
            return rows;
        } catch (RuntimeException | Error failure) {
            recordFailure("deleteById", failure, started, stage);
            throw failure;
        }
    }

    @Override
    public int deleteAll(Collection<T> entities) {
        throw unsupported("deleteAll", true);
    }

    private Object normalizeTargetId(ID id) {
        if (id == null || authorization.getTargetId() == null) {
            throw new PermissionDeniedException("按次授权 DAO 缺少单一主键目标");
        }
        EntityFieldMeta idField = meta.resolveFieldMeta(meta.getIdField());
        Object normalized = JdbcEntityValueBinder.normalize(idField, id);
        Object authorized = JdbcEntityValueBinder.normalize(idField, authorization.getTargetId());
        if (!Objects.equals(normalized, authorized)) {
            throw new PermissionDeniedException("DAO 主键目标与按次授权目标不一致");
        }
        return normalized;
    }

    private List<String> resolveReturnFields() {
        List<String> fields = new ArrayList<String>();
        for (Map.Entry<String, EntityFieldMeta> entry : meta.getFieldMetas().entrySet()) {
            if (!entry.getValue().isRelation()
                && authorization.getFields().allows(AuthorizationFields.Use.RETURN, entry.getKey())) {
                fields.add(entry.getKey());
            }
        }
        if (fields.isEmpty()) {
            throw new PermissionDeniedException("按次授权 DAO 没有可返回字段");
        }
        if (!fields.contains(meta.getIdField())) {
            throw new PermissionDeniedException("按次授权 DAO 必须允许返回主键字段: " + meta.getIdField());
        }
        return Collections.unmodifiableList(fields);
    }

    private void requireOperation(String operation) {
        if (!operation.equals(authorization.getOperation().getOperationCode())) {
            throw new PermissionDeniedException("按次授权 DAO 操作不允许执行: " + operation);
        }
    }

    private boolean hasVersion() {
        EntityFieldMeta version = meta.resolveFieldMeta("version");
        return version != null && !version.isRelation();
    }

    private void requireVersionSupport() {
        if (!hasVersion()) {
            throw new ValidationException("实体未启用 version: " + meta.getEntityName());
        }
        Class<?> type = wrap(meta.resolveFieldMeta("version").getJavaType());
        if (!Number.class.isAssignableFrom(type)) {
            throw new ValidationException("version 字段必须是数字类型: " + meta.getEntityName());
        }
    }

    private Long expectedPatchVersion(UpdatePatch<T> patch) {
        if (!hasVersion()) {
            if (patch.getExpectedVersion() != null) {
                throw new ValidationException("实体未启用 version，不能携带预期版本: " + meta.getEntityName());
            }
            return null;
        }
        requireVersionSupport();
        Long expected = patch.getExpectedVersion();
        if (expected == null && patch.hasField("version")) {
            expected = patch.get("version", Long.class);
        }
        if (expected == null) {
            throw new ValidationException("启用 version 的 Patch 必须携带预期版本: " + meta.getEntityName());
        }
        return expected;
    }

    private Object normalizeVersion(Long version) {
        requireVersionSupport();
        return JdbcEntityValueBinder.normalize(meta.resolveFieldMeta("version"), version);
    }

    private void requireWriteFields(Map<String, Object> changes) {
        if (changes == null || changes.isEmpty()) {
            throw new ValidationException("更新没有可写字段: " + meta.getEntityName());
        }
        for (String field : changes.keySet()) {
            EntityFieldMeta fieldMeta = meta.resolveFieldMeta(field);
            if (fieldMeta == null || fieldMeta.isRelation() || !fieldMeta.isWritable()
                || fieldMeta.isScopeField() || fieldMeta.isImmutable()
                || meta.getIdField().equals(field) || field.equals(meta.getLogicDeleteField())
                || "version".equals(field)
                || !authorization.getFields().allows(AuthorizationFields.Use.WRITE, field)) {
                throw new PermissionDeniedException("本次 DAO 写入不允许字段: " + field);
            }
        }
    }

    private void requireTransactionExecutor() {
        if (transactionExecutor == null) {
            throw new PermissionDeniedException("按次授权 JDBC DAO 写入必须配置事务执行器");
        }
    }

    /** 在整批事务开始前冻结目标、字段、版本、范围和 JDBC 参数。 */
    PreparedUpdate prepareBatchUpdate(ID id, UpdatePatch<T> patch, Long expectedVersion) {
        requireOperation("update");
        Object normalizedId = normalizeTargetId(id);
        NormalizedUpdatePatch normalized = NormalizedUpdatePatch.from(patch, meta);
        Object patchId = JdbcEntityValueBinder.normalize(meta.resolveFieldMeta(meta.getIdField()), normalized.getId());
        if (!Objects.equals(normalizedId, patchId)) {
            throw new ValidationException("Patch 主键与批量更新目标不一致");
        }
        Long version = expectedPatchVersion(patch);
        if (!Objects.equals(version, expectedVersion)) {
            throw new ValidationException("Patch 预期版本与批量请求不一致");
        }
        requireWriteFields(normalized.getChanges());
        return prepareUpdate(normalizedId, normalized.getChanges(), version);
    }

    private int executeUpdate(Object id, Map<String, Object> changes, Long expectedVersion, T entityToBackfill) {
        int result = prepareUpdate(id, changes, expectedVersion).execute();
        if (hasVersion() && entityToBackfill != null) {
            writeField(entityToBackfill, "version", JdbcEntityValueBinder.normalize(
                meta.resolveFieldMeta("version"), Long.valueOf(expectedVersion.longValue() + 1L)
            ));
        }
        return result;
    }

    private PreparedUpdate prepareUpdate(Object id, Map<String, Object> changes, Long expectedVersion) {
        if (hasVersion()) {
            requireVersionSupport();
            if (expectedVersion == null) {
                throw new ValidationException("启用 version 的实体更新必须携带预期版本: " + meta.getEntityName());
            }
        } else if (expectedVersion != null) {
            throw new ValidationException("实体未启用 version，不能携带预期版本: " + meta.getEntityName());
        }
        JdbcAuthorizationScopeCompiler.Predicate scope = scopes.compile(
            authorization.getScope(), meta, dialect
        );
        List<String> assignments = new ArrayList<String>();
        List<Object> args = new ArrayList<Object>();
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            assignments.add(quote(meta.resolveColumn(entry.getKey())) + " = ?");
            args.add(JdbcEntityValueBinder.normalize(
                meta.resolveFieldMeta(entry.getKey()), entry.getValue()
            ));
        }
        if (hasVersion()) {
            String versionColumn = quote(meta.resolveColumn("version"));
            assignments.add(versionColumn + " = " + versionColumn + " + 1");
        }
        List<String> predicates = new ArrayList<String>();
        List<Object> whereArgs = new ArrayList<Object>();
        predicates.add(scope.getSql());
        whereArgs.addAll(scope.getArgs());
        appendLogicDelete(predicates, whereArgs, null);
        predicates.add(quote(meta.resolveColumn(meta.getIdField())) + " = ?");
        whereArgs.add(id);
        if (expectedVersion != null) {
            predicates.add(quote(meta.resolveColumn("version")) + " = ?");
            whereArgs.add(normalizeVersion(expectedVersion));
        }
        args.addAll(whereArgs);
        String sql = "update " + quote(meta.getTable()) + " set " + String.join(",", assignments)
            + " where " + String.join(" and ", predicates);
        return new PreparedUpdate(sql, args);
    }

    final class PreparedUpdate {
        private final String sql;
        private final List<Object> args;

        private PreparedUpdate(String sql, List<Object> args) {
            this.sql = sql;
            this.args = Collections.unmodifiableList(new ArrayList<Object>(args));
        }

        int execute() {
            return requireSingleWrite(executor.update(sql, args, context("updateById")), "updateById");
        }
    }

    private int executeDelete(Object id, Object expectedVersion) {
        JdbcAuthorizationScopeCompiler.Predicate scope = scopes.compile(
            authorization.getScope(), meta, dialect
        );
        List<String> predicates = new ArrayList<String>();
        List<Object> args = new ArrayList<Object>();
        predicates.add(scope.getSql());
        args.addAll(scope.getArgs());
        appendLogicDelete(predicates, args, null);
        predicates.add(quote(meta.resolveColumn(meta.getIdField())) + " = ?");
        args.add(id);
        if (expectedVersion != null) {
            predicates.add(quote(meta.resolveColumn("version")) + " = ?");
            args.add(expectedVersion);
        }
        String sql;
        String logicDeleteField = meta.getLogicDeleteField();
        if (logicDeleteField != null && !logicDeleteField.trim().isEmpty()) {
            sql = "update " + quote(meta.getTable()) + " set "
                + quote(meta.resolveColumn(logicDeleteField)) + " = ?";
            if (expectedVersion != null) {
                String versionColumn = quote(meta.resolveColumn("version"));
                sql += "," + versionColumn + " = " + versionColumn + " + 1";
            }
            sql += " where " + String.join(" and ", predicates);
            args.add(0, JdbcEntityValueBinder.normalize(
                meta.resolveFieldMeta(logicDeleteField), JdbcLogicDeleteValues.deleted(meta)
            ));
        } else {
            sql = "delete from " + quote(meta.getTable()) + " where "
                + String.join(" and ", predicates);
        }
        int rows = executor.update(sql, args, context("deleteById"));
        return requireSingleWrite(rows, "deleteById");
    }

    private int requireSingleWrite(int rows, String operation) {
        if (rows == 0) {
            throw new EntityDaoWriteMissException("目标不存在或不可写: " + meta.getEntityName());
        }
        if (rows != 1) {
            throw new EntityDaoPersistenceException(operation + " 影响行数不是 1: " + rows);
        }
        return 1;
    }

    private void writeField(Object entity, String fieldName, Object value) {
        Field field = resolveField(entity.getClass(), fieldName);
        if (field == null) {
            throw new EntityDaoPersistenceException("实体字段不存在，无法回填版本: " + fieldName);
        }
        try {
            field.setAccessible(true);
            field.set(entity, value);
        } catch (IllegalAccessException | IllegalArgumentException ex) {
            throw new EntityDaoPersistenceException("实体版本无法回填: " + fieldName, ex);
        }
    }

    private Field resolveField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private Class<?> wrap(Class<?> type) {
        if (type == null || !type.isPrimitive()) return type;
        if (type == Long.TYPE) return Long.class;
        if (type == Integer.TYPE) return Integer.class;
        if (type == Short.TYPE) return Short.class;
        if (type == Byte.TYPE) return Byte.class;
        if (type == Double.TYPE) return Double.class;
        if (type == Float.TYPE) return Float.class;
        return type;
    }

    private void appendLogicDelete(List<String> predicates, List<Object> args, String alias) {
        String field = meta.getLogicDeleteField();
        if (field == null || field.trim().isEmpty()) {
            return;
        }
        String column = quote(meta.resolveColumn(field));
        predicates.add((alias == null ? column : alias + "." + column) + " = ?");
        args.add(JdbcEntityValueBinder.normalize(
            meta.resolveFieldMeta(field), JdbcLogicDeleteValues.notDeleted(meta)
        ));
    }

    private String selectColumns() {
        List<String> resolved = returnFields;
        if (resolved == null) {
            synchronized (this) {
                resolved = returnFields;
                if (resolved == null) {
                    resolved = resolveReturnFields();
                    returnFields = resolved;
                }
            }
        }
        List<String> columns = new ArrayList<String>();
        for (String field : resolved) {
            columns.add("t." + quote(meta.resolveColumn(field)) + " as " + quote(field));
        }
        return String.join(",", columns);
    }

    private String quote(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new ValidationException("授权 DAO SQL 标识符不能为空");
        }
        return dialect.quoteIdentifier(identifier);
    }

    private DefaultExecutionContext context() {
        return context("findById");
    }

    private DefaultExecutionContext context(String operation) {
        DefaultExecutionContext context = new DefaultExecutionContext(
            meta.getEntityName() + "|DAO_AUTHORIZED|" + operation, null
        );
        context.getAttributes().put("operationDomain", "ENTITY_DAO_AUTHORIZED");
        context.getAttributes().put("operation", operation);
        context.getAttributes().put("phase", "main");
        return context;
    }

    private void record(
        CrudGovernanceAuditOutcome outcome,
        CrudGovernanceAuditReasonCode reason,
        long started,
        CrudErrorStage stage
    ) {
        record(outcome, reason, started, stage, "findById");
    }

    private void record(
        CrudGovernanceAuditOutcome outcome,
        CrudGovernanceAuditReasonCode reason,
        long started,
        CrudErrorStage stage,
        String operation
    ) {
        AccessSubject subject = AccessContextScope.currentAdmission()
            .filter(admission -> admission.getVisitorContext() == authorization.getVisitor())
            .flatMap(AccessAdmission::getAuthenticatedSubject)
            .orElse(authorization.getVisitor().getSubject().orElse(null));
        String resource = meta.getResourceDescriptor().getResourceCode();
        CrudResourceAction action = new CrudResourceAction(
            resource,
            authorization.getOperation().getOperationCode(),
            null
        );
        audit.record(CrudGovernanceAuditEvent.forBusinessQuery(
            authorization.getVisitor(),
            subject,
            authorization.getResult(),
            action,
            outcome,
            reason,
            (System.nanoTime() - started) / 1_000_000L,
            stage,
            resource + "." + operation
        ));
    }

    void recordCommand(
        String operation,
        CrudGovernanceAuditOutcome outcome,
        CrudGovernanceAuditReasonCode reason,
        long started,
        CrudErrorStage stage
    ) {
        AccessSubject subject = AccessContextScope.currentAdmission()
            .filter(admission -> admission.getVisitorContext() == authorization.getVisitor())
            .flatMap(AccessAdmission::getAuthenticatedSubject)
            .orElse(authorization.getVisitor().getSubject().orElse(null));
        String resource = meta.getResourceDescriptor().getResourceCode();
        CrudResourceAction action = new CrudResourceAction(
            resource,
            authorization.getOperation().getOperationCode(),
            null
        );
        audit.record(CrudGovernanceAuditEvent.forBusinessCommand(
            authorization.getVisitor(),
            subject,
            authorization.getResult(),
            action,
            outcome,
            reason,
            (System.nanoTime() - started) / 1_000_000L,
            stage,
            resource + "." + operation
        ));
    }

    private void recordFailure(String operation, Throwable failure, long started, CrudErrorStage stage) {
        CrudGovernanceAuditOutcome outcome = stage == CrudErrorStage.EXECUTE
            ? CrudGovernanceAuditOutcome.EXECUTION_FAILED
            : CrudGovernanceAuditOutcome.GOVERNANCE_DENIED;
        CrudGovernanceAuditReasonCode reason = failure instanceof EntityDaoWriteMissException
            ? CrudGovernanceAuditReasonCode.EXECUTION_FAILED
            : failure instanceof CrudException
                ? CrudGovernanceAuditReasonCode.fromCrudErrorCode(((CrudException) failure).getErrorCode())
                : CrudGovernanceAuditReasonCode.EXECUTION_FAILED;
        try {
            recordCommand(operation, outcome, reason, started, stage);
        } catch (RuntimeException | Error auditFailure) {
            if (auditFailure != failure) {
                failure.addSuppressed(auditFailure);
            }
        }
    }

    private PermissionDeniedException unsupported(String operation, boolean command) {
        long started = System.nanoTime();
        try {
            if (command) {
                recordCommand(operation, CrudGovernanceAuditOutcome.GOVERNANCE_DENIED,
                    CrudGovernanceAuditReasonCode.UNSUPPORTED_OPERATION, started, CrudErrorStage.GOVERNANCE);
            } else {
                record(CrudGovernanceAuditOutcome.GOVERNANCE_DENIED,
                    CrudGovernanceAuditReasonCode.UNSUPPORTED_OPERATION, started,
                    CrudErrorStage.GOVERNANCE, operation);
            }
        } catch (RuntimeException | Error auditFailure) {
            // 审计失败不能把原本明确的能力拒绝替换成审计异常。
        }
        return new PermissionDeniedException(
            "按次授权 DAO 仅支持已绑定单一目标的单目标操作，当前不支持" + operation
                + "；批量或无目标授权尚未建立逐目标授权、范围交集、事务和审计合同"
        );
    }
}

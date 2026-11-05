package com.entloom.crud.engine.jdbc.command;

import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.api.enums.FilterOperator;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.api.model.QueryFilter;
import com.entloom.crud.core.capability.command.engine.AuthorizedCommandExecutor;
import com.entloom.crud.core.capability.command.engine.AuthorizedBatchUpdateExecutor;
import com.entloom.crud.core.capability.command.spec.BatchCommand;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.command.spec.DeleteTarget;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import com.entloom.crud.core.capability.dao.RowConstraintNormalizer;
import com.entloom.crud.core.capability.dao.InsertConstraintValueBinder;
import com.entloom.crud.core.capability.dao.RowConstraint;
import com.entloom.crud.core.runtime.contract.CrudInputContract;
import com.entloom.crud.core.runtime.validation.RequiredFieldValidator;
import com.entloom.crud.core.runtime.validation.CreateDefaultValueApplier;
import com.entloom.crud.core.governance.authorization.AuthorizationScope;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.CrudExceptionContext;
import com.entloom.crud.core.exception.EntityDaoWriteMissException;
import com.entloom.crud.core.governance.authorization.BusinessPermissionDecision;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionExecutor;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionPolicy;
import com.entloom.crud.core.governance.adapter.CrudBusinessAuthorizationAdapter;
import com.entloom.crud.core.governance.admission.AccessAdmission;
import com.entloom.crud.core.governance.admission.AccessContextScope;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditEvent;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditOutcome;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditReasonCode;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import com.entloom.crud.core.governance.authorization.AuthorizationResult;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.runtime.context.DefaultExecutionContext;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.spec.SpecSnapshotFactory;
import com.entloom.crud.core.runtime.validation.EntityWriteValidator;
import com.entloom.crud.core.security.GuardedSqlExecutor;
import com.entloom.crud.core.security.SqlSecurityGuard;
import com.entloom.crud.engine.jdbc.dialect.JdbcDialect;
import com.entloom.crud.engine.jdbc.dialect.StandardJdbcDialect;
import com.entloom.crud.engine.jdbc.sql.JdbcAuthorizationScopeCompiler;
import com.entloom.crud.engine.jdbc.sql.JdbcLogicDeleteValues;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 根实体按次授权 CREATE/UPDATE/DELETE；批量 UPDATE 复用授权 DAO 执行。 */
public final class JdbcAuthorizedCommandExecutor implements AuthorizedCommandExecutor {
    private final EntityMetaRegistry entities;
    private final CrudBusinessAuthorizationAdapter authorization;
    private final GuardedSqlExecutor sqlExecutor;
    private final SqlSecurityGuard sqlGuard;
    private final JdbcDialect dialect;
    private final CrudGovernanceAuditRecorder audit;
    private final CrudWriteTransactionExecutor transactions;
    private final AuthorizedBatchUpdateExecutor batchUpdates;
    private final EntityWriteValidator validator;
    private final CrudInputContract contracts;
    private final JdbcAuthorizationScopeCompiler scopes = new JdbcAuthorizationScopeCompiler();
    private final CommandPayloadMapper payloadMapper = new CommandPayloadMapper();

    public JdbcAuthorizedCommandExecutor(
        EntityMetaRegistry entities,
        CrudBusinessAuthorizationAdapter authorization,
        GuardedSqlExecutor sqlExecutor,
        SqlSecurityGuard sqlGuard,
        JdbcDialect dialect,
        CrudGovernanceAuditRecorder audit,
        CrudWriteTransactionExecutor transactions
    ) {
        this(entities, authorization, sqlExecutor, sqlGuard, dialect, audit, transactions, null,
            (meta, values) -> {}, CrudInputContract.empty());
    }

    public JdbcAuthorizedCommandExecutor(
        EntityMetaRegistry entities, CrudBusinessAuthorizationAdapter authorization,
        GuardedSqlExecutor sqlExecutor, SqlSecurityGuard sqlGuard, JdbcDialect dialect,
        CrudGovernanceAuditRecorder audit, CrudWriteTransactionExecutor transactions,
        AuthorizedBatchUpdateExecutor batchUpdates, EntityWriteValidator validator, CrudInputContract contracts
    ) {
        this.entities = Objects.requireNonNull(entities, "实体元数据注册表不能为空");
        this.authorization = Objects.requireNonNull(authorization, "CRUD 业务授权适配器不能为空");
        this.sqlExecutor = Objects.requireNonNull(sqlExecutor, "SQL 执行器不能为空");
        this.sqlGuard = Objects.requireNonNull(sqlGuard, "SQL 安全守卫不能为空");
        this.dialect = dialect == null ? StandardJdbcDialect.GENERIC : dialect;
        this.audit = Objects.requireNonNull(audit, "治理审计记录器不能为空");
        this.transactions = transactions;
        this.batchUpdates = batchUpdates;
        this.validator = Objects.requireNonNull(validator, "写入校验器不能为空");
        this.contracts = Objects.requireNonNull(contracts, "输入契约不能为空");
    }

    /** 仅供不需要事务能力探测的构造场景使用；执行时仍会拒绝缺失事务执行器。 */
    public JdbcAuthorizedCommandExecutor(
        EntityMetaRegistry entities,
        CrudBusinessAuthorizationAdapter authorization,
        GuardedSqlExecutor sqlExecutor,
        SqlSecurityGuard sqlGuard,
        JdbcDialect dialect,
        CrudGovernanceAuditRecorder audit
    ) {
        this(entities, authorization, sqlExecutor, sqlGuard, dialect, audit, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <P, R> R action(VisitorContext visitor, CommandSpec<P> source) {
        if (source != null && source.getOp() == CommandOperation.UPDATE_BATCH && batchUpdates != null) {
            return batchUpdates.action(visitor, source);
        }
        long started = System.nanoTime();
        Invocation invocation = new Invocation(source);
        try {
            if (visitor == null) {
                throw new PermissionDeniedException("新策略命令缺少可信访问者；异步执行必须重新准入并打开作用域");
            }
            CommandSpec<P> spec = prepare(source);
            invocation.spec = spec;
            EntityMeta meta = requireMeta(spec);
            Target existingTarget = spec.getOp() == CommandOperation.CREATE ? null : target(meta, spec);
            String targetId = existingTarget == null ? null : existingTarget.targetId;
            invocation.operation = spec.getOp();
            invocation.meta = meta;
            invocation.targetId = targetId;
            invocation.stage = CrudErrorStage.GOVERNANCE;
            AuthorizationResult result = authorization.authorize(visitor, spec.toBuilder()
                .subject(null).targetFilters(Collections.<QueryFilter>emptyList()).build(), targetId);
            invocation.result = result;
            requireApplicable(visitor, meta, spec.getOp(), targetId, result);
            Target target = existingTarget == null ? createTarget(meta, spec) : existingTarget;
            if (spec.getOp() == CommandOperation.UPDATE) {
                requireWriteFields(meta, target.values, result.getFields());
                try {
                    contracts.validateUpdate(target.values, meta);
                } catch (IllegalArgumentException invalid) {
                    throw new ValidationException(invalid.getMessage());
                }
                validator.validate(meta, target.values);
            } else if (spec.getOp() == CommandOperation.CREATE) {
                prepareCreate(meta, target, result);
            }
            CommandSpec<P> guardSpec = spec.toBuilder()
                .payload((P) new WriteCommand<Object>(spec.getOp(), target.id, target.values,
                    Collections.<QueryFilter>emptyList(), target.expectedVersion)).build();
            sqlGuard.beforeCompile(guardSpec);
            if (transactions == null) {
                throw new PermissionDeniedException("受授权 JDBC 写入必须配置事务执行器");
            }
            invocation.stage = CrudErrorStage.EXECUTE;
            R value = transactions.execute(CrudWriteTransactionPolicy.SINGLE_TRANSACTION,
                () -> (R) executeSql(visitor, spec, meta, target, result));
            record(visitor, invocation, CrudGovernanceAuditOutcome.SUCCESS,
                CrudGovernanceAuditReasonCode.NONE, started);
            return value;
        } catch (RuntimeException | Error failure) {
            CrudGovernanceAuditOutcome outcome = invocation.stage == CrudErrorStage.EXECUTE
                ? CrudGovernanceAuditOutcome.EXECUTION_FAILED : CrudGovernanceAuditOutcome.GOVERNANCE_DENIED;
            CrudGovernanceAuditReasonCode reason = invocation.result != null
                && invocation.result.getKind() == BusinessPermissionDecision.Kind.TARGET_INACCESSIBLE
                ? CrudGovernanceAuditReasonCode.DATA_SCOPE_DENIED : failure instanceof EntityDaoWriteMissException
                ? CrudGovernanceAuditReasonCode.EXECUTION_FAILED
                : failure instanceof CrudException
                    ? CrudGovernanceAuditReasonCode.fromCrudErrorCode(((CrudException) failure).getErrorCode())
                    : CrudGovernanceAuditReasonCode.EXECUTION_FAILED;
            try {
                record(visitor, invocation, outcome, reason, started);
            } catch (RuntimeException | Error auditFailure) {
                if (auditFailure != failure) {
                    failure.addSuppressed(auditFailure);
                }
            }
            if (failure instanceof RuntimeException) {
                throw CrudExceptionContext.enrich((RuntimeException) failure, invocation.stage, null);
            }
            throw failure;
        }
    }

    private <P> CommandSpec<P> prepare(CommandSpec<P> source) {
        if (source == null) {
            throw new ValidationException("命令规范(spec)不能为空");
        }
        CommandSpec<P> spec = SpecSnapshotFactory.copy(source);
        if (spec.getOp() != CommandOperation.CREATE && spec.getOp() != CommandOperation.UPDATE && spec.getOp() != CommandOperation.DELETE) {
            throw new PermissionDeniedException("受授权 JDBC 命令仅支持单条 CREATE、单目标 UPDATE/DELETE");
        }
        if (spec.getRootType() == null || spec.getEntityClasses().size() > 1
            || (spec.getEntityClasses().size() == 1 && spec.getEntityClasses().get(0) != spec.getRootType())
            || (spec.getScene() != null && !spec.getScene().trim().isEmpty())
            || spec.getAccessDecision() != AccessDecision.ALLOW || spec.getGrantedScope() != null
            || spec.getGovernanceScope() != null) {
            throw new PermissionDeniedException("受授权 JDBC 命令仅支持普通根实体单目标写入");
        }
        // 旧 Spec 主体只允许作为输入快照存在，不能进入 SQL 安全校验或授权内核。
        return spec.toBuilder().subject(null).build();
    }

    private EntityMeta requireMeta(CommandSpec<?> spec) {
        EntityMeta meta = entities.getEntityMeta(spec.getRootType());
        if (meta == null || meta.getEntityType() != spec.getRootType()) {
            throw new PermissionDeniedException("CRUD 根实体没有规范资源元数据");
        }
        if (meta.getIdPolicy() == EntityIdPolicy.COMPOSITE || meta.getIdField() == null
            || meta.getIdField().trim().isEmpty() || meta.resolveColumn(meta.getIdField()) == null) {
            throw new PermissionDeniedException("受授权 JDBC 写入仅支持单列主键");
        }
        return meta;
    }

    private Target target(EntityMeta meta, CommandSpec<?> spec) {
        Object payload = spec.getPayload();
        if (spec.getOp() == CommandOperation.UPDATE && payload instanceof DeleteTarget) {
            throw new ValidationException("UPDATE 不接受 DeleteTarget");
        }
        if (payload instanceof BatchCommand<?>) {
            throw new PermissionDeniedException("受授权 JDBC 命令不支持批量目标");
        }
        Object id = null;
        Object valuesPayload = payload;
        List<QueryFilter> commandFilters = Collections.emptyList();
        Long commandVersion = null;
        if (payload instanceof DeleteTarget) {
            DeleteTarget target = (DeleteTarget) payload;
            id = target.getId();
            commandFilters = target.getTargetFilters();
            commandVersion = target.getExpectedVersion();
            valuesPayload = Collections.emptyMap();
        } else if (payload instanceof WriteCommand<?>) {
            WriteCommand<?> command = (WriteCommand<?>) payload;
            if (command.getOp() != null && command.getOp() != spec.getOp()) {
                throw new ValidationException("WriteCommand.op 与 CommandSpec.op 不一致");
            }
            id = command.getId();
            commandFilters = command.getTargetFilters();
            commandVersion = command.getExpectedVersion();
            valuesPayload = command.getValues();
        }
        Map<String, Object> values = payloadMapper.toMap(valuesPayload);
        Object valueId = values.get(meta.getIdField());
        if (id != null && valueId != null && !sameValue(id, valueId)) {
            throw new ValidationException("目标 ID 与 payload 主键字段不一致: " + meta.getIdField());
        }
        if (spec.getOp() == CommandOperation.UPDATE && payload instanceof WriteCommand<?> && valueId != null) {
            throw new PermissionDeniedException("更新载荷不能包含主键字段: " + meta.getIdField());
        }
        if (id == null) {
            id = valueId;
        }
        values.remove(meta.getIdField());
        if (spec.getOp() == CommandOperation.DELETE && !values.isEmpty()) {
            throw new PermissionDeniedException("DELETE 不接受写入字段");
        }
        if (id == null) {
            id = idFromFilters(meta, commandFilters.isEmpty() ? spec.getTargetFilters() : commandFilters);
        } else {
            verifyTargetFilters(meta, id, commandFilters.isEmpty() ? spec.getTargetFilters() : commandFilters);
        }
        id = scalarId(id, meta);
        Long expectedVersion = commandVersion == null ? spec.getExpectedVersion() : commandVersion;
        EntityFieldMeta versionField = meta.resolveFieldMeta("version");
        if (spec.getOp() == CommandOperation.UPDATE && values.containsKey("version")) {
            Object value = RowConstraintNormalizer.normalizeValue(versionField, values.remove("version"));
            if (!(value instanceof Number)) {
                throw new ValidationException("预期版本必须是非负整数");
            }
            Long payloadVersion = Long.valueOf(((Number) value).longValue());
            if (expectedVersion != null && !expectedVersion.equals(payloadVersion)) {
                throw new ValidationException("更新载荷与命令的版本条件不一致");
            }
            expectedVersion = payloadVersion;
        }
        if (expectedVersion != null && meta.resolveFieldMeta("version") == null) {
            throw new ValidationException("实体没有 version 字段，不能使用 expectedVersion");
        }
        if (versionField != null) {
            Class<?> versionType = versionField.getJavaType();
            if (versionType != Long.class && versionType != long.class && versionType != Integer.class && versionType != int.class) {
                throw new ValidationException("version 字段必须是 Long 或 Integer");
            }
            if (expectedVersion == null || expectedVersion.longValue() < 0L) {
                throw new ValidationException("启用 version 的实体写入必须携带非负预期版本");
            }
        }
        return new Target(id, String.valueOf(id), values, expectedVersion);
    }

    private Target createTarget(EntityMeta meta, CommandSpec<?> spec) {
        Object payload = spec.getPayload();
        Object id = null;
        if (spec.isDryRun() || (spec.getIdempotencyKey() != null && !spec.getIdempotencyKey().isBlank())) {
            throw new ValidationException("CREATE 不支持试运行或幂等键");
        }
        if (!spec.getTargetFilters().isEmpty() || spec.getExpectedVersion() != null
            || payload instanceof BatchCommand<?> || payload instanceof DeleteTarget) {
            throw new PermissionDeniedException("CREATE 不接受目标条件、预期版本或批量目标");
        }
        if (payload instanceof WriteCommand<?>) {
            WriteCommand<?> command = (WriteCommand<?>) payload;
            if (command.getOp() != null && command.getOp() != CommandOperation.CREATE
                || !command.getTargetFilters().isEmpty() || command.getExpectedVersion() != null) {
                throw new ValidationException("CREATE 命令形状不一致");
            }
            id = command.getId();
            payload = command.getValues();
        }
        Map<String, Object> values = new LinkedHashMap<>(payloadMapper.toMap(payload));
        Object valueId = values.remove(meta.getIdField());
        if (id != null && valueId != null && !sameValue(id, valueId)) {
            throw new ValidationException("CREATE 主键与载荷不一致");
        }
        if (id == null) id = valueId;
        if (meta.getIdPolicy() == EntityIdPolicy.GENERATED) {
            if (id != null) throw new ValidationException("GENERATED 主键实体不能显式指定主键");
        } else {
            id = RowConstraintNormalizer.normalizeValue(meta.resolveFieldMeta(meta.getIdField()), scalarId(id, meta));
        }
        // 创建授权绑定业务操作，不借未来的主键冒充已有目标。
        return new Target(id, null, values, null);
    }

    private void prepareCreate(EntityMeta meta, Target target, AuthorizationResult result) {
        for (String name : target.values.keySet()) {
            EntityFieldMeta field = meta.resolveFieldMeta(name);
            if (field == null || field.isRelation() || field.isImmutable()
                || name.equals(meta.getLogicDeleteField()) || "version".equals(name)
                || (!field.isScopeField() && (!field.isWritable()
                    || !result.getFields().allows(AuthorizationFields.Use.WRITE, name)))) {
                throw new PermissionDeniedException("创建不允许输入字段: " + name);
            }
            if (field.isScopeField() && !containsScopeField(result.getScope(), name)) {
                throw new PermissionDeniedException("创建缺少字段归属授权: " + name);
            }
        }
        new RequiredFieldValidator(contracts).validateCreateValues(target.values, meta, target.id);
        new CreateDefaultValueApplier().applyToValues(target.values, meta);
        target.values.replaceAll((name, value) -> RowConstraintNormalizer.normalizeValue(meta.resolveFieldMeta(name), value));
        Map<String, Object> scoped = InsertConstraintValueBinder.bind(insertConstraint(result.getScope()), meta, target.values);
        target.values.clear();
        target.values.putAll(scoped);
        if (meta.getLogicDeleteField() != null && !meta.getLogicDeleteField().isBlank()) {
            target.values.put(meta.getLogicDeleteField(), JdbcLogicDeleteValues.notDeleted(meta));
        }
        EntityFieldMeta version = meta.resolveFieldMeta("version");
        if (version != null) {
            Class<?> type = version.getJavaType();
            if (type != Long.class && type != long.class && type != Integer.class && type != int.class) {
                throw new ValidationException("version 字段必须是 Long 或 Integer");
            }
            target.values.put("version", RowConstraintNormalizer.normalizeValue(version, 0L));
        }
        if (target.id != null) target.values.put(meta.getIdField(), target.id);
        validator.validateCreate(meta, target.values);
    }

    private boolean containsScopeField(AuthorizationScope scope, String field) {
        if (field.equals(scope.getField())) return true;
        return scope.getChildren().stream().anyMatch(child -> containsScopeField(child, field));
    }

    /** 创建只接受可确定的归属范围；复用 DAO 的范围填充与冲突校验。 */
    private RowConstraint insertConstraint(AuthorizationScope scope) {
        switch (scope.getKind()) {
            case ALL: return RowConstraint.unrestricted();
            case EQUAL: return RowConstraint.eq(scope.getField(), scope.getValues().get(0));
            case IN: return RowConstraint.in(scope.getField(), scope.getValues());
            case AND:
                return RowConstraint.and(scope.getChildren().stream().map(this::insertConstraint).toArray(RowConstraint[]::new));
            default: throw new PermissionDeniedException("CREATE 不支持授权范围: " + scope.getKind());
        }
    }

    private Object idFromFilters(EntityMeta meta, List<QueryFilter> filters) {
        if (filters == null || filters.size() != 1) {
            throw new PermissionDeniedException("单目标写入必须提供唯一主键目标");
        }
        QueryFilter filter = filters.get(0);
        if (filter == null || !meta.getIdField().equals(filter.getField())
            || filter.getOperator() != FilterOperator.EQ || filter.getValue() == null
            || filter.getValue() instanceof Collection<?> || filter.getValue().getClass().isArray()) {
            throw new PermissionDeniedException("单目标写入目标必须是主键 EQ 标量");
        }
        return filter.getValue();
    }

    private void verifyTargetFilters(EntityMeta meta, Object id, List<QueryFilter> filters) {
        if (filters == null || filters.isEmpty()) return;
        Object selected = idFromFilters(meta, filters);
        if (!sameValue(id, selected)) {
            throw new ValidationException("目标 ID 与目标过滤条件不一致");
        }
    }

    private Object scalarId(Object id, EntityMeta meta) {
        if (id == null || id instanceof Collection<?> || id.getClass().isArray()
            || id instanceof Map<?, ?> || String.valueOf(id).trim().isEmpty()
            || (id instanceof String && !id.equals(((String) id).trim()))) {
            throw new PermissionDeniedException("单目标写入必须使用非空主键标量: " + meta.getIdField());
        }
        return id;
    }

    private void requireApplicable(VisitorContext visitor, EntityMeta meta, CommandOperation operation,
                                   String targetId, AuthorizationResult result) {
        if (result == null
            || result.getRequest().getVisitor() != visitor
            || !Objects.equals(result.getRequest().getTargetId(), targetId)
            || !meta.getResourceDescriptor().getResourceCode().equals(result.getRequest().getOperation().getResourceCode())) {
            throw new PermissionDeniedException("授权结果不适用于本次根实体写入");
        }
        if (result.getKind() == BusinessPermissionDecision.Kind.TARGET_INACCESSIBLE && targetId != null
            && (operation == CommandOperation.UPDATE || operation == CommandOperation.DELETE)) {
            EntityDaoWriteMissException inaccessible = new EntityDaoWriteMissException("目标不存在或不可写: " + meta.getEntityName());
            inaccessible.withStage(CrudErrorStage.EXECUTE);
            throw inaccessible;
        }
        if (result.getDecision() != AccessDecision.ALLOW) {
            throw new PermissionDeniedException("业务授权被拒绝");
        }
    }

    private void requireWriteFields(EntityMeta meta, Map<String, Object> values, AuthorizationFields fields) {
        if (values.isEmpty()) {
            throw new ValidationException("更新载荷没有可更新字段");
        }
        for (String field : values.keySet()) {
            EntityFieldMeta fieldMeta = meta.resolveFieldMeta(field);
            if (fieldMeta == null || fieldMeta.isRelation() || !fieldMeta.isWritable()
                || fieldMeta.isScopeField() || fieldMeta.isImmutable() || meta.getIdField().equals(field)
                || field.equals(meta.getLogicDeleteField()) || "version".equals(field)
                || !fields.allows(AuthorizationFields.Use.WRITE, field)) {
                throw new PermissionDeniedException("本次写入不允许字段: " + field);
            }
        }
    }

    private Object executeSql(VisitorContext visitor, CommandSpec<?> spec, EntityMeta meta,
                              Target target, AuthorizationResult result) {
        if (spec.getOp() == CommandOperation.CREATE) return executeCreate(spec, meta, target);
        JdbcAuthorizationScopeCompiler.Predicate scope = scopes.compile(result.getScope(), meta, dialect);
        List<String> predicates = new ArrayList<String>();
        List<Object> whereArgs = new ArrayList<Object>();
        appendNotDeleted(meta, predicates, whereArgs);
        predicates.add(scope.getSql());
        whereArgs.addAll(scope.getArgs());
        String idColumn = dialect.quoteIdentifier(meta.resolveColumn(meta.getIdField()));
        predicates.add(idColumn + " = ?");
        whereArgs.add(target.id);
        if (target.expectedVersion != null) {
            predicates.add(dialect.quoteIdentifier(meta.resolveColumn("version")) + " = ?");
            whereArgs.add(target.expectedVersion);
        }
        String where = String.join(" and ", predicates);
        List<Object> args = new ArrayList<Object>();
        String sql;
        if (spec.getOp() == CommandOperation.UPDATE) {
            List<String> fields = new ArrayList<String>(target.values.keySet());
            List<String> assignments = new ArrayList<String>();
            for (String field : fields) {
                assignments.add(dialect.quoteIdentifier(meta.resolveColumn(field)) + " = ?");
                args.add(target.values.get(field));
            }
            if (meta.resolveFieldMeta("version") != null) {
                String versionColumn = dialect.quoteIdentifier(meta.resolveColumn("version"));
                assignments.add(versionColumn + " = " + versionColumn + " + 1");
            }
            sql = "update " + dialect.quoteIdentifier(meta.getTable()) + " set "
                + String.join(",", assignments) + " where " + where;
        } else if (meta.getLogicDeleteField() != null && !meta.getLogicDeleteField().trim().isEmpty()) {
            String field = meta.getLogicDeleteField();
            String column = dialect.quoteIdentifier(meta.resolveColumn(field));
            if (column == null) throw new ValidationException("未知逻辑删除字段: " + field);
            sql = "update " + dialect.quoteIdentifier(meta.getTable()) + " set " + column + " = ? where " + where;
            args.add(JdbcLogicDeleteValues.deleted(meta));
        } else {
            sql = "delete from " + dialect.quoteIdentifier(meta.getTable()) + " where " + where;
        }
        args.addAll(whereArgs);
        int rows = sqlExecutor.update(sql, args, context(spec));
        if (rows == 0) throw new EntityDaoWriteMissException("目标不存在或不可写: " + meta.getEntityName());
        if (rows != 1) throw new ValidationException("单目标写入影响行数异常: " + rows);
        return result(spec, rows);
    }

    private Object executeCreate(CommandSpec<?> spec, EntityMeta meta, Target target) {
        if (target.values.isEmpty()) throw new ValidationException("创建载荷没有可写字段");
        List<String> columns = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        target.values.forEach((name, value) -> {
            EntityFieldMeta field = meta.resolveFieldMeta(name);
            if (field == null || field.isRelation() || meta.resolveColumn(name) == null) {
                throw new ValidationException("创建字段没有持久化列: " + name);
            }
            columns.add(dialect.quoteIdentifier(meta.resolveColumn(name)));
            args.add(value);
        });
        String sql = "insert into " + dialect.quoteIdentifier(meta.getTable()) + " (" + String.join(",", columns)
            + ") values (" + String.join(",", Collections.nCopies(args.size(), "?")) + ")";
        Object id = target.id;
        if (meta.getIdPolicy() == EntityIdPolicy.GENERATED) {
            Object generated = sqlExecutor.insertAndReturnGeneratedKey(sql, args, context(spec));
            if (generated == null) throw new ValidationException("数据库生成主键未回填: " + meta.getIdField());
            id = RowConstraintNormalizer.normalizeValue(meta.resolveFieldMeta(meta.getIdField()), scalarId(generated, meta));
        } else if (sqlExecutor.update(sql, args, context(spec)) != 1) {
            throw new ValidationException("单条创建影响行数异常");
        }
        return result(spec, 1, id);
    }

    private void appendNotDeleted(EntityMeta meta, List<String> predicates, List<Object> args) {
        String field = meta.getLogicDeleteField();
        if (field == null || field.trim().isEmpty()) return;
        String column = dialect.quoteIdentifier(meta.resolveColumn(field));
        if (column == null) throw new ValidationException("未知逻辑删除字段: " + field);
        predicates.add(column + " = ?");
        args.add(JdbcLogicDeleteValues.notDeleted(meta));
    }

    private <R> R result(CommandSpec<?> spec, int rows) {
        return result(spec, rows, null);
    }

    @SuppressWarnings("unchecked")
    private <R> R result(CommandSpec<?> spec, int rows, Object createdId) {
        if (spec.getResultType() == Void.class) return null;
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("rows", rows);
        if (createdId != null) data.put("id", createdId);
        Class<?> type = spec.getResultType();
        if (type != null && CommandResult.class.isAssignableFrom(type)) return (R) CommandResult.success(data);
        if (type != null && Number.class.isAssignableFrom(type)) return (R) Integer.valueOf(rows);
        return (R) data;
    }

    private DefaultExecutionContext context(CommandSpec<?> spec) {
        DefaultExecutionContext context = new DefaultExecutionContext("authorized-command/"
            + spec.getRootType().getName() + "/" + spec.getOp().name(), spec.getScene());
        context.getAttributes().put("operationDomain", "COMMAND");
        context.getAttributes().put("operation", spec.getOp().name());
        context.getAttributes().put("phase", "authorized");
        return context;
    }

    private void record(VisitorContext visitor, Invocation invocation, CrudGovernanceAuditOutcome outcome,
                        CrudGovernanceAuditReasonCode reason, long started) {
        EntityMeta meta = invocation.meta;
        String resource = meta == null ? null : meta.getResourceDescriptor().getResourceCode();
        CrudResourceAction action = invocation.result == null
            ? (resource == null ? null : new CrudResourceAction(resource,
                invocation.operation == null ? null : invocation.operation.name(), null))
            : new CrudResourceAction(invocation.result.getRequest().getOperation().getResourceCode(),
                invocation.result.getRequest().getOperation().getOperationCode(), null);
        AccessSubject subject = visitor == null ? null : AccessContextScope.currentAdmission()
            .filter(admission -> admission.getVisitorContext() == visitor)
            .flatMap(AccessAdmission::getAuthenticatedSubject).orElse(visitor.getSubject().orElse(null));
        CrudGovernanceAuditEvent event = CrudGovernanceAuditEvent.forBusinessCommand(visitor, subject,
            invocation.result, action, outcome, reason, (System.nanoTime() - started) / 1_000_000L,
            outcome == CrudGovernanceAuditOutcome.SUCCESS ? null : invocation.stage,
            resource == null ? null : resource + "." + (invocation.operation == null ? "COMMAND" : invocation.operation.name()));
        audit.record(event);
    }

    private boolean sameValue(Object left, Object right) {
        if (left instanceof Number && right instanceof Number) {
            return new java.math.BigDecimal(String.valueOf(left)).compareTo(new java.math.BigDecimal(String.valueOf(right))) == 0;
        }
        return Objects.equals(left, right);
    }

    private static final class Target {
        private final Object id;
        private final String targetId;
        private final Map<String, Object> values;
        private final Long expectedVersion;

        private Target(Object id, String targetId, Map<String, Object> values, Long expectedVersion) {
            this.id = id;
            this.targetId = targetId;
            this.values = new LinkedHashMap<String, Object>(values);
            this.expectedVersion = expectedVersion;
        }
    }

    private static final class Invocation {
        private CommandSpec<?> spec;
        private EntityMeta meta;
        private CommandOperation operation;
        private AuthorizationResult result;
        private String targetId;
        private CrudErrorStage stage = CrudErrorStage.NORMALIZE;

        private Invocation(CommandSpec<?> spec) { this.spec = spec; }
    }

}

package com.entloom.crud.core.capability.command.engine;

import com.entloom.crud.api.access.AccessSubject;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.api.enums.AccessDecision;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.api.model.CommandResult;
import com.entloom.crud.core.capability.command.patch.DefaultCommandPayloadBinder;
import com.entloom.crud.core.capability.command.patch.UpdatePatch;
import com.entloom.crud.core.capability.command.spec.BatchCommand;
import com.entloom.crud.core.capability.command.spec.CommandSpec;
import com.entloom.crud.core.capability.command.spec.WriteCommand;
import com.entloom.crud.core.capability.dao.EntityDaoAuthorizationContext;
import com.entloom.crud.core.capability.dao.EntityDaoBatchAuthorizationContext;
import com.entloom.crud.core.capability.dao.EntityDaoBatchWrite;
import com.entloom.crud.core.capability.dao.EntityDaoBatchWriteRequest;
import com.entloom.crud.core.capability.dao.EntityDaoFactory;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.capability.dao.RowConstraintNormalizer;
import com.entloom.crud.core.exception.CrudExceptionContext;
import com.entloom.crud.core.exception.CrudException;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.adapter.CrudBusinessAuthorizationAdapter;
import com.entloom.crud.core.governance.admission.AccessAdmission;
import com.entloom.crud.core.governance.admission.AccessContextScope;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditEvent;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditOutcome;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditReasonCode;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import com.entloom.crud.core.governance.authorization.AuthorizationResult;
import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.runtime.contract.CrudInputContract;
import com.entloom.crud.core.runtime.meta.EntityFieldMeta;
import com.entloom.crud.core.runtime.meta.EntityIdPolicy;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import com.entloom.crud.core.runtime.spec.SpecSnapshotFactory;
import com.entloom.crud.core.runtime.validation.EntityWriteValidator;
import com.entloom.crud.core.security.SqlSecurityGuard;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 通用批量局部更新；复用授权 DAO 的全量原子写入、版本及提交后审计。 */
public final class AuthorizedBatchUpdateExecutor implements AuthorizedCommandExecutor {
    private final EntityMetaRegistry entities;
    private final CrudBusinessAuthorizationAdapter authorization;
    private final EntityDaoFactory daos;
    private final SqlSecurityGuard sqlGuard;
    private final CrudGovernanceAuditRecorder audit;
    private final CrudInputContract inputContract;
    private final EntityWriteValidator validator;
    private final DefaultCommandPayloadBinder binder = new DefaultCommandPayloadBinder();

    public AuthorizedBatchUpdateExecutor(EntityMetaRegistry entities, CrudBusinessAuthorizationAdapter authorization,
        EntityDaoFactory daos, SqlSecurityGuard sqlGuard, CrudGovernanceAuditRecorder audit,
        CrudInputContract inputContract, EntityWriteValidator validator) {
        this.entities = Objects.requireNonNull(entities, "实体元数据不能为空");
        this.authorization = Objects.requireNonNull(authorization, "业务授权适配器不能为空");
        this.daos = Objects.requireNonNull(daos, "实体 DAO 工厂不能为空");
        this.sqlGuard = Objects.requireNonNull(sqlGuard, "SQL 安全守卫不能为空");
        this.audit = Objects.requireNonNull(audit, "治理审计不能为空");
        this.inputContract = Objects.requireNonNull(inputContract, "输入契约不能为空");
        this.validator = Objects.requireNonNull(validator, "更新校验器不能为空");
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <P, R> R action(VisitorContext visitor, CommandSpec<P> source) {
        long started = System.nanoTime();
        EntityMeta meta = null;
        List<AuthorizationResult> results = new ArrayList<AuthorizationResult>();
        boolean delegated = false;
        try {
            CommandSpec<P> spec = prepare(visitor, source);
            meta = entities.getEntityMeta(spec.getRootType());
            if (meta == null || meta.getEntityType() != spec.getRootType()
                || meta.getIdPolicy() == EntityIdPolicy.COMPOSITE || meta.resolveFieldMeta(meta.getIdField()) == null) {
                throw new PermissionDeniedException("批量更新必须使用已注册的单列主键实体");
            }
            List<WriteCommand<?>> commands = new ArrayList<WriteCommand<?>>(((BatchCommand<?>) spec.getPayload()).getItems());
            if (commands.isEmpty() || commands.size() > EntityDaoBatchAuthorizationContext.DEFAULT_MAX_TARGETS) {
                throw new ValidationException("每批更新须包含 1～" + EntityDaoBatchAuthorizationContext.DEFAULT_MAX_TARGETS + " 项");
            }
            List<UpdatePatch<Object>> patches = new ArrayList<UpdatePatch<Object>>();
            Set<String> ids = new LinkedHashSet<String>();
            for (WriteCommand<?> command : commands) {
                UpdatePatch<Object> patch = patch(meta, command);
                if (!ids.add(String.valueOf(patch.getId()))) {
                    throw new ValidationException("批量更新目标不能重复: " + patch.getId());
                }
                patches.add(patch);
            }
            List<EntityDaoAuthorizationContext> contexts = new ArrayList<EntityDaoAuthorizationContext>();
            for (UpdatePatch<Object> patch : patches) {
                AuthorizationResult result = authorization.authorize(visitor, spec, String.valueOf(patch.getId()));
                results.add(result);
                if (result == null || result.getDecision() != AccessDecision.ALLOW) {
                    throw new PermissionDeniedException("批量更新业务授权被拒绝");
                }
                contexts.add(EntityDaoAuthorizationContext.of(result.getRequest(), result));
            }
            EntityDaoBatchAuthorizationContext context = EntityDaoBatchAuthorizationContext.of(contexts);
            List<EntityDaoBatchWriteRequest.Item<Object, Object>> items = new ArrayList<EntityDaoBatchWriteRequest.Item<Object, Object>>();
            for (UpdatePatch<Object> patch : patches) {
                Map<String, Object> values = binder.bindFieldMap(patch.getEntity(), meta);
                values.keySet().retainAll(patch.getPresentFields());
                validator.validate(meta, values);
                sqlGuard.beforeCompile(spec.toBuilder().subject(null).op(CommandOperation.UPDATE)
                    .payload((P) new WriteCommand<Object>(CommandOperation.UPDATE, patch.getId(), patch.getValuesForDelegate(),
                        Collections.emptyList(), patch.getExpectedVersion())).build());
                items.add(EntityDaoBatchWriteRequest.Item.update(String.valueOf(patch.getId()), patch.getId(), patch));
            }
            EntityType type = EntityType.of(meta.getEntityType(), meta.resolveFieldMeta(meta.getIdField()).getJavaType());
            EntityDaoBatchWrite<Object, Object> writer = daos.scopedAuthorizedBatchWrite(type, context);
            EntityDaoBatchWriteRequest<Object, Object> request = EntityDaoBatchWriteRequest.of(context, items);
            // 从此处起由底层负责失败与最终提交审计，避免网关重复记录。
            delegated = true;
            writer.execute(request);
            return (R) result(spec, patches);
        } catch (RuntimeException | Error failure) {
            if (!delegated) {
                recordDenied(visitor, meta, results, started, failure);
            }
            if (failure instanceof RuntimeException) {
                throw CrudExceptionContext.enrich((RuntimeException) failure,
                    delegated ? CrudErrorStage.EXECUTE : CrudErrorStage.GOVERNANCE, null);
            }
            throw failure;
        }
    }

    private <P> CommandSpec<P> prepare(VisitorContext visitor, CommandSpec<P> source) {
        if (visitor == null) throw new PermissionDeniedException("批量更新缺少可信访问者");
        if (source == null) throw new ValidationException("命令规范不能为空");
        CommandSpec<P> spec = SpecSnapshotFactory.copy(source).toBuilder().subject(null).build();
        if (spec.getOp() != CommandOperation.UPDATE_BATCH || !(spec.getPayload() instanceof BatchCommand<?>)) {
            throw new ValidationException("批量更新必须提供 UPDATE_BATCH 和结构化批量载荷");
        }
        if (spec.getRootType() == null || spec.getEntityClasses().size() > 1
            || (spec.getEntityClasses().size() == 1 && spec.getEntityClasses().get(0) != spec.getRootType())
            || (spec.getScene() != null && !spec.getScene().trim().isEmpty())
            || spec.getAccessDecision() != AccessDecision.ALLOW || spec.getGrantedScope() != null
            || spec.getGovernanceScope() != null) {
            throw new PermissionDeniedException("批量更新仅支持普通根实体按主键写入");
        }
        if (!spec.getTargetFilters().isEmpty() || spec.getExpectedVersion() != null || spec.isDryRun()
            || (spec.getIdempotencyKey() != null && !spec.getIdempotencyKey().trim().isEmpty())) {
            throw new ValidationException("批量更新不支持全局目标、全局版本、试运行或幂等键");
        }
        return spec;
    }

    @SuppressWarnings("unchecked")
    private UpdatePatch<Object> patch(EntityMeta meta, WriteCommand<?> command) {
        if (command == null || command.getOp() != CommandOperation.UPDATE || command.getId() == null
            || !command.getTargetFilters().isEmpty()) {
            throw new ValidationException("批量更新项必须提供 UPDATE、主键，且不能携带目标过滤条件");
        }
        if (command.getId() instanceof Map<?, ?> || command.getId() instanceof Iterable<?>
            || command.getId().getClass().isArray()) {
            throw new ValidationException("批量更新主键必须是非空标量");
        }
        Map<String, Object> values = new LinkedHashMap<String, Object>(binder.bindFieldMap(command.getValues(), null));
        if (values.containsKey(meta.getIdField()) || values.containsKey("id")) {
            throw new ValidationException("结构化批量更新的 values 不能包含主键");
        }
        inputContract.validateUpdate(values, meta);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            EntityFieldMeta field = meta.resolveFieldMeta(entry.getKey());
            if (field == null || field.isRelation()) {
                throw new PermissionDeniedException("批量更新不允许字段: " + entry.getKey());
            }
            entry.setValue(RowConstraintNormalizer.normalizeValue(field, entry.getValue()));
        }
        Object id = RowConstraintNormalizer.normalizeValue(meta.resolveFieldMeta(meta.getIdField()), command.getId());
        if (id instanceof Map<?, ?> || id instanceof Iterable<?> || id.getClass().isArray()
            || String.valueOf(id).trim().isEmpty() || !String.valueOf(id).equals(String.valueOf(id).trim())) {
            throw new ValidationException("批量更新主键必须是非空标量");
        }
        if (command.getExpectedVersion() != null) {
            if (values.containsKey("version") && !Objects.equals(String.valueOf(values.get("version")), String.valueOf(command.getExpectedVersion()))) {
                throw new ValidationException("批量更新项的版本条件不一致");
            }
            values.put("version", command.getExpectedVersion());
        }
        if (values.containsKey("version") && (meta.resolveFieldMeta("version") == null || values.get("version") == null
            || new java.math.BigDecimal(String.valueOf(values.get("version"))).signum() < 0)) {
            throw new ValidationException("预期版本必须是非负整数，且实体须启用 version");
        }
        values.put(meta.getIdField(), id);
        return binder.bindUpdatePatch(values, (Class<Object>) meta.getEntityType(), meta);
    }

    private Object result(CommandSpec<?> spec, List<UpdatePatch<Object>> patches) {
        if (spec.getResultType() == Void.class) return null;
        if (spec.getResultType() != null && Number.class.isAssignableFrom(spec.getResultType())) return Integer.valueOf(patches.size());
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (UpdatePatch<Object> patch : patches) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("operation", "UPDATE");
            item.put("id", patch.getId());
            item.put("rows", 1);
            items.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("rows", patches.size());
        data.put("items", items);
        return spec.getResultType() != null && CommandResult.class.isAssignableFrom(spec.getResultType())
            ? CommandResult.success(data) : data;
    }

    private void recordDenied(VisitorContext visitor, EntityMeta meta, List<AuthorizationResult> results,
        long started, Throwable failure) {
        List<AuthorizationResult> attempts = results.isEmpty() ? Collections.singletonList(null) : results;
        for (AuthorizationResult result : attempts) {
            try {
                String resource = meta == null ? null : meta.getResourceDescriptor().getResourceCode();
                CrudResourceAction action = resource == null ? null : new CrudResourceAction(resource,
                    result == null ? "UPDATE_BATCH" : result.getRequest().getOperation().getOperationCode(), null);
                AccessSubject subject = visitor == null ? null : AccessContextScope.currentAdmission()
                    .filter(admission -> admission.getVisitorContext() == visitor)
                    .flatMap(AccessAdmission::getAuthenticatedSubject).orElse(visitor.getSubject().orElse(null));
                audit.record(CrudGovernanceAuditEvent.forBusinessCommand(visitor, subject, result, action,
                    CrudGovernanceAuditOutcome.GOVERNANCE_DENIED, failure instanceof CrudException
                        ? CrudGovernanceAuditReasonCode.fromCrudErrorCode(((CrudException) failure).getErrorCode())
                        : CrudGovernanceAuditReasonCode.EXECUTION_FAILED,
                    (System.nanoTime() - started) / 1_000_000L, CrudErrorStage.GOVERNANCE,
                    resource == null ? null : resource + ".UPDATE_BATCH"));
            } catch (RuntimeException | Error auditFailure) {
                if (auditFailure != failure) failure.addSuppressed(auditFailure);
            }
        }
    }
}

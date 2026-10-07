package com.entloom.crud.engine.jdbc.dao;

import com.entloom.crud.api.enums.CrudErrorStage;
import com.entloom.crud.core.capability.dao.EntityDaoAuthorizationContext;
import com.entloom.crud.core.capability.dao.EntityDaoBatchAuthorizationContext;
import com.entloom.crud.core.capability.dao.EntityDaoBatchExecutionResult;
import com.entloom.crud.core.capability.dao.EntityDaoBatchWrite;
import com.entloom.crud.core.capability.dao.EntityDaoBatchWriteOperation;
import com.entloom.crud.core.capability.dao.EntityDaoBatchWriteRequest;
import com.entloom.crud.core.capability.dao.EntityType;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.foundation.write.CrudBatchTransactionCapability;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionExecutor;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditOutcome;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditReasonCode;
import com.entloom.crud.core.governance.audit.CrudGovernanceAuditRecorder;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import com.entloom.crud.core.security.GuardedSqlExecutor;
import com.entloom.crud.engine.jdbc.dialect.JdbcDialect;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 逐目标范围、字段和版本独立消费，整批提交后才产生成功结果。 */
final class JdbcAuthorizedEntityDaoBatchWrite<T, ID> implements EntityDaoBatchWrite<T, ID> {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcAuthorizedEntityDaoBatchWrite.class);
    private final EntityDaoBatchAuthorizationContext authorization;
    private final List<JdbcAuthorizedEntityDao<T, ID>> delegates = new ArrayList<JdbcAuthorizedEntityDao<T, ID>>();
    private final CrudWriteTransactionExecutor transactions;

    JdbcAuthorizedEntityDaoBatchWrite(
        EntityType<T, ID> type, EntityMeta meta, EntityDaoBatchAuthorizationContext authorization,
        GuardedSqlExecutor executor, JdbcDialect dialect, CrudGovernanceAuditRecorder audit,
        CrudWriteTransactionExecutor transactions
    ) {
        this.authorization = authorization;
        this.transactions = transactions;
        for (EntityDaoAuthorizationContext target : authorization.getAuthorizations()) {
            delegates.add(new JdbcAuthorizedEntityDao<T, ID>(type, meta, target, executor, dialect, audit, transactions));
        }
    }

    @Override
    public EntityDaoBatchAuthorizationContext getAuthorization() {
        return authorization;
    }

    @Override
    public EntityDaoBatchExecutionResult execute(EntityDaoBatchWriteRequest<T, ID> request) {
        long started = System.nanoTime();
        // 另一调用的授权请求不能进入本批次审计。
        Objects.requireNonNull(request, "批量写入请求不能为空").requireApplicable(authorization);
        List<JdbcAuthorizedEntityDao<T, ID>.PreparedUpdate> prepared =
            new ArrayList<JdbcAuthorizedEntityDao<T, ID>.PreparedUpdate>();
        try {
            request.getAtomicity().requireSupportedBy(transactions);
            for (int i = 0; i < request.getItems().size(); i++) {
                EntityDaoBatchWriteRequest.Item<T, ID> item = request.getItems().get(i);
                if (item.getOperation() != EntityDaoBatchWriteOperation.UPDATE) {
                    throw new PermissionDeniedException("JDBC 逐目标批量写入当前只支持 UPDATE");
                }
                prepared.add(delegates.get(i).prepareBatchUpdate(item.getId(), item.getPatch(), item.getExpectedVersion()));
            }
        } catch (RuntimeException | Error failure) {
            recordAll(CrudGovernanceAuditOutcome.GOVERNANCE_DENIED, started, failure);
            throw failure;
        }
        List<EntityDaoBatchExecutionResult.Item> results = new ArrayList<EntityDaoBatchExecutionResult.Item>();
        try {
            ((CrudBatchTransactionCapability) transactions).executeCommittedBatch(() -> {
                for (JdbcAuthorizedEntityDao<T, ID>.PreparedUpdate update : prepared) {
                    update.execute();
                }
                return null;
            });
        } catch (RuntimeException | Error failure) {
            recordAll(CrudGovernanceAuditOutcome.EXECUTION_FAILED, started, failure);
            throw failure;
        }
        // 失败抛出原异常；回滚目标不能被误写成 SUCCEEDED，也不回填 Patch 实体的版本。
        for (String targetId : authorization.getTargetIds()) {
            results.add(EntityDaoBatchExecutionResult.Item.succeeded(targetId));
        }
        recordAll(CrudGovernanceAuditOutcome.SUCCESS, started, null);
        return EntityDaoBatchExecutionResult.of(authorization, results, request.getAtomicity());
    }

    private void recordAll(CrudGovernanceAuditOutcome outcome, long started, Throwable failure) {
        for (JdbcAuthorizedEntityDao<T, ID> delegate : delegates) {
            try {
                delegate.recordCommand("batchUpdate", outcome,
                    outcome == CrudGovernanceAuditOutcome.SUCCESS ? CrudGovernanceAuditReasonCode.NONE
                        : outcome == CrudGovernanceAuditOutcome.GOVERNANCE_DENIED
                            ? CrudGovernanceAuditReasonCode.PERMISSION_DENIED : CrudGovernanceAuditReasonCode.EXECUTION_FAILED,
                    started, outcome == CrudGovernanceAuditOutcome.SUCCESS ? null
                        : outcome == CrudGovernanceAuditOutcome.GOVERNANCE_DENIED ? CrudErrorStage.GOVERNANCE : CrudErrorStage.EXECUTE);
            } catch (RuntimeException auditFailure) {
                if (failure != null && failure != auditFailure) {
                    failure.addSuppressed(auditFailure);
                }
                // 已提交的操作不能因审计故障变成可重试失败；记录故障并继续其余目标审计。
                LOG.warn("逐目标批量写入最终审计失败，结果为 {}", outcome, auditFailure);
            }
        }
    }
}

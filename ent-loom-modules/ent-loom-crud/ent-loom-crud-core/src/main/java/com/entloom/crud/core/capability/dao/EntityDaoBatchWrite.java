package com.entloom.crud.core.capability.dao;

import java.util.List;

/** 消费逐目标授权上下文的批量写入 DAO 合同。 */
public interface EntityDaoBatchWrite<T, ID> {
    /**
     * 按请求中授权目标顺序执行全量原子写入。
     *
     * <p>适配器必须在事务、版本条件和审计能力不足时拒绝，不得退回旧范围 DAO。</p>
     */
    EntityDaoBatchExecutionResult execute(EntityDaoBatchWriteRequest<T, ID> request);

    /** 返回本批次绑定的授权目标顺序。 */
    default List<String> getTargetIds() {
        return getAuthorization().getTargetIds();
    }

    /** 返回本次签发的授权上下文，用于构造同一调用的写入请求。 */
    EntityDaoBatchAuthorizationContext getAuthorization();
}

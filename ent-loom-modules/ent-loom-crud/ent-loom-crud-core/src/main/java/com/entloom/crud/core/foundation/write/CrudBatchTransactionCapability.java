package com.entloom.crud.core.foundation.write;

import com.entloom.crud.core.capability.dao.EntityDaoBatchAtomicity;

/**
 * 批量事务执行器的能力声明。
 *
 * <p>未实现本接口的事务执行器不能被批量适配器推断为支持任何原子性语义。</p>
 */
public interface CrudBatchTransactionCapability {
    /** 是否能兑现指定批量原子性声明。 */
    boolean supportsBatchAtomicity(EntityDaoBatchAtomicity atomicity);

    /**
     * 在返回前完成整批提交或回滚，供最终结果和审计使用。
     *
     * <p>仅声明原子性不代表拥有最终提交边界；默认拒绝。实现不得在外层事务尚未提交时返回成功。</p>
     */
    default <T> T executeCommittedBatch(CrudWriteTransactionCallback<T> callback) {
        throw new com.entloom.crud.core.exception.ValidationException("事务执行器未实现批量最终提交边界");
    }
}

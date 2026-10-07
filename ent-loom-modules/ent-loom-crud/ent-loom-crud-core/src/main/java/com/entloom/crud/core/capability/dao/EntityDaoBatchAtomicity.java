package com.entloom.crud.core.capability.dao;

import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.foundation.write.CrudBatchTransactionCapability;
import com.entloom.crud.core.foundation.write.CrudWriteTransactionExecutor;

/** 逐目标批量结果的原子性声明；存储适配器仍需提供对应事务能力。 */
public enum EntityDaoBatchAtomicity {
    /** 默认全量原子：任一失败不得提交其它目标的写入。 */
    ALL_OR_NOTHING,
    /** 显式允许部分结果；调用方必须自行处理逐目标失败和补偿。 */
    EXPLICIT_PARTIAL

    ;

    /** 判断事务执行器是否显式声明支持本原子性。 */
    public boolean isSupportedBy(CrudWriteTransactionExecutor executor) {
        if (!(executor instanceof CrudBatchTransactionCapability)) {
            return false;
        }
        return ((CrudBatchTransactionCapability) executor).supportsBatchAtomicity(this);
    }

    /** 在批量存储访问前要求事务执行器明确声明能力。 */
    public void requireSupportedBy(CrudWriteTransactionExecutor executor) {
        if (executor == null) {
            throw new ValidationException("批量事务执行器不能为空");
        }
        if (!isSupportedBy(executor)) {
            throw new ValidationException("事务执行器未声明支持批量原子性: " + this);
        }
    }
}

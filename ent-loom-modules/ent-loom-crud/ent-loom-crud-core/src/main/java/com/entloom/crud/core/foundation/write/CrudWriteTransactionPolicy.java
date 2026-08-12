package com.entloom.crud.core.foundation.write;

/**
 * 写入事务策略。
 */
public enum CrudWriteTransactionPolicy {
    /** 不由 CRUD Import 链路控制事务。 */
    NONE,
    /** 整个导入任务使用一个事务。 */
    SINGLE_TRANSACTION,
    /** 每个批次独立事务。 */
    PER_BATCH
}

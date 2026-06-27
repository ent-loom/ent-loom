package com.entloom.crud.core.foundation.taskfile;

/**
 * Import / Export 异步任务状态。
 */
public enum CrudTaskStatus {
    /** 已创建，等待执行。 */
    PENDING,
    /** 正在执行。 */
    RUNNING,
    /** 已成功完成。 */
    SUCCEEDED,
    /** 执行失败。 */
    FAILED,
    /** 已取消。 */
    CANCELED,
    /** 文件或任务已过期。 */
    EXPIRED
}

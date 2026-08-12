package com.entloom.crud.api.enums;

/**
 * 统一错误码定义。
 */
public enum CrudErrorCode {
    /** 参数校验错误。 */
    VALIDATION_ERROR,
    /** 类型解析失败。 */
    TYPE_RESOLUTION_FAILED,
    /** 实体范围非法。 */
    ENTITY_SCOPE_ILLEGAL,
    /** 方法不允许。 */
    METHOD_NOT_ALLOWED,
    /** 实体未暴露。 */
    ENTITY_NOT_EXPOSED,
    /** 路由未命中。 */
    ROUTE_NOT_FOUND,
    /** 路由歧义。 */
    ROUTE_AMBIGUOUS,
    /** 单条语义命中多条。 */
    QUERY_NOT_UNIQUE,
    /** 查询策略不支持。 */
    UNSUPPORTED_QUERY_STRATEGY,
    /** 接口权限拒绝。 */
    PERMISSION_DENIED,
    /** 数据范围拒绝。 */
    DATA_SCOPE_DENIED,
    /** 幂等处理中。 */
    IDEMPOTENCY_IN_PROGRESS,
    /** 幂等载荷冲突。 */
    IDEMPOTENCY_PAYLOAD_CONFLICT,
    /** 幂等键缺失。 */
    IDEMPOTENCY_KEY_REQUIRED,
    /** Spec 属性贡献失败。 */
    ATTRIBUTE_CONTRIBUTION_FAILED,
    /** 格式不支持。 */
    UNSUPPORTED_FORMAT,
    /** 操作不支持。 */
    UNSUPPORTED_OPERATION,
    /** 场景未命中。 */
    SCENE_NOT_FOUND,
    /** 文件服务不可用。 */
    FILE_SERVICE_UNAVAILABLE,
    /** 文件不存在。 */
    FILE_NOT_FOUND,
    /** 文件已过期。 */
    FILE_EXPIRED,
    /** 文件元数据非法。 */
    FILE_METADATA_INVALID,
    /** 任务不存在。 */
    TASK_NOT_FOUND,
    /** 下载尚未就绪。 */
    DOWNLOAD_NOT_READY,
    /** 超过同步处理上限。 */
    SYNC_LIMIT_EXCEEDED,
    /** 行级校验失败。 */
    ROW_VALIDATION_FAILED,
    /** 内部错误。 */
    INTERNAL_ERROR
}

package com.entloom.meta.contract.enums;

/**
 * 关系解析完整度。
 */
public enum RelationResolutionStatus {
    /** 已解析声明字段和目标实体字段。 */
    RESOLVED,
    /** 仅解析了本地声明侧，目标侧需要 adapter/registry 继续解析。 */
    PARTIALLY_RESOLVED,
    /** 解析结果不可用。 */
    INVALID
}

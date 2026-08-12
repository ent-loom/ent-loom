package com.entloom.crud.api.enums;

/**
 * CRUD 失败阶段。
 */
public enum CrudErrorStage {
    /** HTTP 请求合同解析或校验阶段。 */
    HTTP_CONTRACT,
    /** 网关请求快照规范化阶段。 */
    NORMALIZE,
    /** 权限、主体、数据范围治理阶段。 */
    GOVERNANCE,
    /** 路由解析阶段。 */
    ROUTE,
    /** 默认引擎或场景 Handler 执行阶段。 */
    EXECUTE,
    /** 未能归类的阶段。 */
    UNKNOWN
}

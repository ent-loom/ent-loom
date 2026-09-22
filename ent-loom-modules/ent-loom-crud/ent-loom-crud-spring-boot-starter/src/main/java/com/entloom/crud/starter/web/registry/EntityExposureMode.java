package com.entloom.crud.starter.web.registry;

/** 通用 HTTP 接口的实体暴露策略。 */
public enum EntityExposureMode {
    /** 仅暴露显式包含的实体，空清单不暴露。 */
    EXPLICIT,
    /** 暴露所有已注册且允许 HTTP 暴露的 CRUD 实体。 */
    ALL_REGISTERED
}

package com.entloom.crud.api.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 请求主体上下文。
 */
@Getter
@Setter
public class SubjectContext {
    /** 主体标识。 */
    private String subjectId;
    /** 租户标识。 */
    private String tenantId;
    /** 组织标识。 */
    private String orgId;
}

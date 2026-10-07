package com.entloom.crud.api.access;

/** 已认证主体的来源类型；匿名访问不构造主体。 */
public enum AccessSubjectType {
    /** 人员。 */
    HUMAN,
    /** 服务。 */
    SERVICE,
    /** 设备。 */
    DEVICE
}

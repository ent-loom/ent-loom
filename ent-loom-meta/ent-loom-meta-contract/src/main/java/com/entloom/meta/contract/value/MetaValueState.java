package com.entloom.meta.contract.value;

/**
 * 元数据值声明状态。
 */
public enum MetaValueState {
    /** 用户或业务代码显式声明。 */
    EXPLICIT,
    /** 由框架推断。 */
    INFERRED,
    /** 由默认值补齐。 */
    DEFAULTED,
    /** 无法可靠判断显式性。 */
    UNKNOWN
}

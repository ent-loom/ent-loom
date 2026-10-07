package com.entloom.crud.api.access;

/** 接口认证模式；缺省使用 REQUIRED，公共访问仍需显式业务策略授权。 */
public enum AuthenticationMode {
    /** 必须提供有效凭证。 */
    REQUIRED,
    /** 无凭证走公共分支，有效凭证走当前主体分支。 */
    OPTIONAL,
    /** 始终走公共分支，但已提供的凭证仍必须验证。 */
    ANONYMOUS
}

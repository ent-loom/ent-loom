package com.entloom.crud.api.access;

/** 认证准入后的策略分支，不代表资源操作已获授权。 */
public enum AccessBranch {
    /** 显式公共策略，不合并主体或身份权限。 */
    PUBLIC,
    /** 已认证主体策略，业务操作按需要求当前身份。 */
    AUTHENTICATED
}

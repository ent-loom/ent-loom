package com.entloom.crud.api.access;

/**
 * 项目的当前业务身份快照。
 *
 * <p>实现必须不可变，不得直接暴露持久化实体或可变集合。code 应来自项目身份枚举的稳定编码，
 * ID 保留项目身份标识语义，不要求等于主体 ID。实现本接口不代表身份已通过认证或获得权限。</p>
 */
public interface BusinessIdentity {
    String getIdentityCode();

    String getIdentityId();
}

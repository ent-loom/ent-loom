package com.entloom.crud.api.access;

/**
 * 项目的当前业务身份快照。
 *
 * <p>身份以稳定业务 code 区分；简单身份使用框架的配置快照，复杂身份可自行实现。
 * 实现必须不可变，不得直接暴露持久化实体或可变集合。ID 不要求等于主体 ID。
 * 实现本接口不代表身份已通过认证或获得权限。</p>
 */
public interface BusinessIdentity {
    String getIdentityCode();

    String getIdentityId();
}

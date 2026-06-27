package com.entloom.crud.core.runtime.spec;

import java.util.Collection;

/**
 * 业务保留 Spec 属性 key 提供器。
 */
public interface CrudSpecReservedAttributeKeyProvider {
    /**
     * 返回需要从手动 attributes 中剥离的业务保留 key。
     *
     * @return 保留 key 集合
     */
    Collection<String> reservedAttributeKeys();
}

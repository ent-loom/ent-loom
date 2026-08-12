package com.entloom.crud.core.adapter;

import com.entloom.crud.core.runtime.spec.BaseSpec;

/**
 * 访问入口解析 SPI。
 */
public interface AccessEntryResolver {
    /** 框架标准 accessEntry 属性键。 */
    String ATTRIBUTE_KEY = "crudAccessEntry";
    /** 默认访问入口。 */
    String DEFAULT_ENTRY = "base";

    /**
     * 从已治理属性中解析访问入口。
     *
     * @param spec 当前 spec
     * @return 访问入口
     */
    String resolveAccessEntry(BaseSpec spec);
}

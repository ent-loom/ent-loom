package com.entloom.crud.core.adapter;

import com.entloom.crud.core.runtime.spec.BaseSpec;

/**
 * 可信调用入口形态解析 SPI。
 */
public interface PortalResolver {
    /** 框架标准 portal 属性键。 */
    String ATTRIBUTE_KEY = "crudPortal";

    /**
     * 从治理后的服务端属性中解析入口形态。
     *
     * @param spec 当前 spec
     * @return 入口形态；未提供时返回 null
     */
    String resolvePortal(BaseSpec spec);
}

package com.entloom.crud.core.runtime.spec;

import java.util.Map;

/**
 * Spec 属性合并器。
 */
public interface CrudSpecAttributeResolver {
    /**
     * 解析当前 Spec 的有效 attributes。
     *
     * @param spec 当前 Spec
     * @return 合并后的 attributes
     */
    Map<String, Object> resolve(BaseSpec spec);
}

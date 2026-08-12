package com.entloom.crud.core.runtime.spec;

import java.util.Map;

/**
 * 服务端 Spec 属性贡献器。
 */
public interface CrudSpecAttributeContributor {
    /**
     * 返回需要合并到当前 Spec 的服务端可信属性。
     *
     * @param spec 已补齐并规范化主体后的 Spec 快照
     * @return 属性映射；返回 null 或空映射表示不贡献属性
     */
    Map<String, Object> contribute(BaseSpec spec);
}

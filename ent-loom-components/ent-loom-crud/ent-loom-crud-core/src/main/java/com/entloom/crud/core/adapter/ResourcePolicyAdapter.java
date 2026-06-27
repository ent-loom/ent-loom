package com.entloom.crud.core.adapter;

import com.entloom.crud.core.governance.model.CrudResourceAction;
import com.entloom.crud.core.runtime.spec.BaseSpec;

/**
 * 业务资源策略适配 SPI。
 *
 * @param <P> 业务策略类型
 */
public interface ResourcePolicyAdapter<P> {
    /**
     * 按当前资源动作与 spec 获取业务资源策略。
     */
    P requirePolicy(CrudResourceAction action, BaseSpec spec);
}

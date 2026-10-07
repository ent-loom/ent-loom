package com.entloom.crud.core.governance.fact;

import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.core.governance.policy.EntityPolicyRegistry;

/** 项目按次读取当前关系事实；不得把请求参数或旧任务快照当作授权事实。 */
@FunctionalInterface
public interface RelationFactResolver {
    /** targetId 仅是待校验目标；超出 maxTuples 时应拒绝或返回超限结果，不得静默截断。 */
    RelationFactSet resolve(VisitorContext visitor, EntityPolicyRegistry.EffectiveRule rule,
                            String targetId, int maxTuples);
}

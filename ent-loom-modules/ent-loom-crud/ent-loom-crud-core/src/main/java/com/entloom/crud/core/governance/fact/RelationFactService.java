package com.entloom.crud.core.governance.fact;

import com.entloom.crud.api.access.AccessBranch;
import com.entloom.crud.api.access.BusinessIdentity;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.policy.EntityPolicyRegistry;
import com.entloom.crud.core.governance.policy.PolicyIdentityBranch;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 不缓存关系事实，每次调用重新查询并校验有效规则要求的角色。 */
public final class RelationFactService {
    private final RelationFactResolver resolver;
    private final int maxTuples;

    public RelationFactService(RelationFactResolver resolver, int maxTuples) {
        this.resolver = Objects.requireNonNull(resolver, "关系事实解析器不能为空");
        if (maxTuples < 1) throw new ValidationException("关系事实元组上限必须大于 0");
        this.maxTuples = maxTuples;
    }

    public RelationFactSet resolve(VisitorContext visitor, EntityPolicyRegistry.EffectiveRule rule, String targetId) {
        Objects.requireNonNull(visitor, "访问者上下文不能为空");
        Objects.requireNonNull(rule, "有效策略规则不能为空");
        if (targetId != null && (targetId.trim().isEmpty() || !targetId.equals(targetId.trim()))) {
            throw new ValidationException("目标 ID 不能包含首尾空格或为空");
        }
        Set<String> roles = rule.getRelationFields().keySet();
        if (roles.isEmpty()) throw new ValidationException("当前规则没有待解析的关系角色: " + rule.getOperation());
        PolicyIdentityBranch branch = rule.getBranch();
        VisitorContext effectiveVisitor = visitor;
        if (branch.getBranch() == AccessBranch.AUTHENTICATED) {
            BusinessIdentity identity = visitor.getIdentity().orElse(null);
            if (identity == null || !branch.getIdentityCode().equals(identity.getIdentityCode())) {
                throw new PermissionDeniedException("当前身份不满足关系事实规则: " + rule.getOperation());
            }
        } else {
            effectiveVisitor = VisitorContext.anonymous(visitor.getAccessEntry(), visitor.getRequestId(), visitor.getTraceId());
        }
        RelationFactSet facts = resolver.resolve(effectiveVisitor, rule, targetId, maxTuples);
        if (facts == null || facts.getTuples().isEmpty()) {
            throw new PermissionDeniedException("缺少当前有效关系事实: " + rule.getOperation());
        }
        if (facts.getTuples().size() > maxTuples) {
            throw new PermissionDeniedException("关系事实元组超过执行上限: " + rule.getOperation());
        }
        for (Map<String, String> tuple : facts.getTuples()) {
            if (!tuple.keySet().equals(roles)) {
                throw new PermissionDeniedException("关系事实角色与有效规则不一致: " + rule.getOperation());
            }
        }
        return facts;
    }
}

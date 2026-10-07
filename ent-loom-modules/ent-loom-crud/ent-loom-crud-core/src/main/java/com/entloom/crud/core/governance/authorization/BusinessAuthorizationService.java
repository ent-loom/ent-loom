package com.entloom.crud.core.governance.authorization;

import com.entloom.crud.api.access.AccessBranch;
import com.entloom.crud.api.access.AuthorizationRequest;
import com.entloom.crud.api.access.BusinessIdentity;
import com.entloom.crud.api.access.VisitorContext;
import com.entloom.crud.core.exception.PermissionDeniedException;
import com.entloom.crud.core.governance.fact.RelationFactService;
import com.entloom.crud.core.governance.policy.BusinessOperationCatalog;
import com.entloom.crud.core.governance.policy.EntityPolicyRegistry;
import com.entloom.crud.core.governance.policy.PolicyIdentityBranch;
import java.util.Objects;

/** 独立于 CRUD Spec 的单一业务操作授权入口。 */
public final class BusinessAuthorizationService {
    private final BusinessOperationCatalog operations;
    private final EntityPolicyRegistry policies;
    private final BusinessPermissionEvaluator evaluator;
    private final RelationFactService facts;

    public BusinessAuthorizationService(BusinessOperationCatalog operations, EntityPolicyRegistry policies,
                                        BusinessPermissionEvaluator evaluator) {
        this(operations, policies, evaluator, null);
    }

    public BusinessAuthorizationService(BusinessOperationCatalog operations, EntityPolicyRegistry policies,
                                        BusinessPermissionEvaluator evaluator, RelationFactService facts) {
        this.operations = Objects.requireNonNull(operations, "业务操作目录不能为空");
        this.policies = Objects.requireNonNull(policies, "实体策略注册表不能为空");
        this.evaluator = Objects.requireNonNull(evaluator, "动态权限判定器不能为空");
        this.facts = facts;
    }

    /** 是否已绑定新策略；缺少操作声明或可信上下文不能使此资源退回旧治理。 */
    public boolean governs(String resourceCode) {
        return policies.containsResource(resourceCode);
    }

    public AuthorizationResult authorize(AuthorizationRequest request) {
        Objects.requireNonNull(request, "授权请求不能为空");
        operations.require(request.getOperation());
        VisitorContext visitor = request.getVisitor();
        BusinessIdentity identity = visitor.getIdentity().orElse(null);
        PolicyIdentityBranch branch = identity == null ? PolicyIdentityBranch.publicBranch()
            : PolicyIdentityBranch.identity(identity.getIdentityCode());
        EntityPolicyRegistry.EffectiveRule rule = policies.requireDeclaration(request.getOperation(), branch);
        VisitorContext effectiveVisitor = branch.getBranch() == AccessBranch.PUBLIC
            ? VisitorContext.anonymous(visitor.getAccessEntry(), visitor.getRequestId(), visitor.getTraceId()) : visitor;
        BusinessPermissionDecision decision = evaluator.evaluate(effectiveVisitor, request.getOperation(), rule,
            request.getTargetId());
        if (decision == null || decision.getKind() != BusinessPermissionDecision.Kind.ALLOW) {
            return AuthorizationResult.denied(request);
        }
        AuthorizationScope scope = decision.getScope();
        if (!rule.getRelationFields().isEmpty()) {
            if (facts == null) {
                throw new PermissionDeniedException("关系事实解析器未配置: " + request.getOperation());
            }
            scope = AuthorizationScope.intersect(scope,
                RelationScopeCompiler.compile(rule, facts.resolve(effectiveVisitor, rule, request.getTargetId())));
        }
        return AuthorizationResult.granted(request, scope, decision.getFields().intersect(rule.getFieldLimit()));
    }
}

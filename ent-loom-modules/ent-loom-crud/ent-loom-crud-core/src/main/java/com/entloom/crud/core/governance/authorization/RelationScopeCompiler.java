package com.entloom.crud.core.governance.authorization;

import com.entloom.crud.core.governance.fact.RelationFactSet;
import com.entloom.crud.core.governance.policy.EntityPolicyRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 按实体字段绑定保留关系事实的元组配对。 */
final class RelationScopeCompiler {
    private RelationScopeCompiler() {
    }

    static AuthorizationScope compile(EntityPolicyRegistry.EffectiveRule rule, RelationFactSet facts) {
        Objects.requireNonNull(rule, "有效规则不能为空");
        Objects.requireNonNull(facts, "关系事实不能为空");
        List<AuthorizationScope> alternatives = new ArrayList<AuthorizationScope>();
        for (Map<String, String> tuple : facts.getTuples()) {
            List<AuthorizationScope> pairs = new ArrayList<AuthorizationScope>();
            for (Map.Entry<String, String> binding : rule.getRelationFields().entrySet()) {
                pairs.add(AuthorizationScope.equal(binding.getValue(), tuple.get(binding.getKey())));
            }
            alternatives.add(pairs.size() == 1 ? pairs.get(0) : AuthorizationScope.and(pairs));
        }
        return alternatives.size() == 1 ? alternatives.get(0) : AuthorizationScope.or(alternatives);
    }
}

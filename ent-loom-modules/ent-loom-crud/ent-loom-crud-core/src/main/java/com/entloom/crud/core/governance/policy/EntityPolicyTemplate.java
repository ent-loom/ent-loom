package com.entloom.crud.core.governance.policy;

import com.entloom.crud.core.exception.ValidationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 项目复用的静态操作规则；业务关系角色由实体绑定提供实际字段。 */
public final class EntityPolicyTemplate {
    private final String code;
    private final String source;
    private final List<Rule> rules;

    public EntityPolicyTemplate(String code, String source, Collection<Rule> rules) {
        this.code = PolicyIdentityBranch.requireCode(code, "模板 code");
        this.source = PolicyIdentityBranch.requireCode(source, "模板来源");
        Objects.requireNonNull(rules, "模板规则不能为空");
        List<Rule> copy = new ArrayList<Rule>();
        Map<PolicyIdentityBranch, Set<String>> keys = new LinkedHashMap<PolicyIdentityBranch, Set<String>>();
        for (Rule rule : rules) {
            if (rule == null) {
                throw new ValidationException("模板规则不能为空: " + code + "，来源: " + source);
            }
            Set<String> operations = keys.get(rule.getBranch());
            if (operations == null) {
                operations = new LinkedHashSet<String>();
                keys.put(rule.getBranch(), operations);
            }
            if (!operations.add(rule.getOperationCode())) {
                throw new ValidationException("模板规则重复: " + code + "/" + rule.getOperationCode()
                    + "/" + rule.getBranch() + "，来源: " + source);
            }
            copy.add(rule);
        }
        if (copy.isEmpty()) {
            throw new ValidationException("模板至少需要一条操作规则: " + code + "，来源: " + source);
        }
        this.rules = Collections.unmodifiableList(copy);
    }

    public String getCode() {
        return code;
    }

    public String getSource() {
        return source;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public static final class Rule {
        private final String operationCode;
        private final PolicyIdentityBranch branch;
        private final Set<String> requiredRelations;

        public Rule(String operationCode, PolicyIdentityBranch branch, Collection<String> requiredRelations) {
            this.operationCode = PolicyIdentityBranch.requireCode(operationCode, "操作 code");
            this.branch = Objects.requireNonNull(branch, "身份分支不能为空");
            Set<String> roles = new LinkedHashSet<String>();
            if (requiredRelations != null) {
                for (String role : requiredRelations) {
                    if (!roles.add(PolicyIdentityBranch.requireCode(role, "关系角色"))) {
                        throw new ValidationException("关系角色重复: " + role + "，操作: " + operationCode);
                    }
                }
            }
            this.requiredRelations = Collections.unmodifiableSet(roles);
        }

        public String getOperationCode() {
            return operationCode;
        }

        public PolicyIdentityBranch getBranch() {
            return branch;
        }

        public Set<String> getRequiredRelations() {
            return requiredRelations;
        }
    }
}

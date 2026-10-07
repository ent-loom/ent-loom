package com.entloom.crud.core.governance.policy;

import java.util.Objects;

/** 实体对模板规则的一处显式差异，按操作和身份分支精确定位。 */
public final class EntityPolicyOverride {
    public enum Kind {
        /** 仅增加模板中不存在的规则。 */
        ADD,
        /** 整体替换模板中的规则。 */
        REPLACE,
        /** 移除模板中的规则。 */
        REMOVE
    }

    private final Kind kind;
    private final String operationCode;
    private final PolicyIdentityBranch branch;
    private final EntityPolicyTemplate.Rule rule;
    private final String source;

    private EntityPolicyOverride(Kind kind, String operationCode, PolicyIdentityBranch branch,
                                 EntityPolicyTemplate.Rule rule, String source) {
        this.kind = kind;
        this.operationCode = operationCode;
        this.branch = branch;
        this.rule = rule;
        this.source = PolicyIdentityBranch.requireCode(source, "差异来源");
    }

    public static EntityPolicyOverride add(EntityPolicyTemplate.Rule rule, String source) {
        return withRule(Kind.ADD, rule, source);
    }

    public static EntityPolicyOverride replace(EntityPolicyTemplate.Rule rule, String source) {
        return withRule(Kind.REPLACE, rule, source);
    }

    public static EntityPolicyOverride remove(String operationCode, PolicyIdentityBranch branch, String source) {
        return new EntityPolicyOverride(Kind.REMOVE,
            PolicyIdentityBranch.requireCode(operationCode, "操作 code"),
            Objects.requireNonNull(branch, "身份分支不能为空"), null, source);
    }

    private static EntityPolicyOverride withRule(Kind kind, EntityPolicyTemplate.Rule rule, String source) {
        Objects.requireNonNull(rule, "差异规则不能为空");
        return new EntityPolicyOverride(kind, rule.getOperationCode(), rule.getBranch(), rule, source);
    }

    public Kind getKind() {
        return kind;
    }

    public String getOperationCode() {
        return operationCode;
    }

    public PolicyIdentityBranch getBranch() {
        return branch;
    }

    public EntityPolicyTemplate.Rule getRule() {
        return rule;
    }

    public String getSource() {
        return source;
    }
}

package com.entloom.crud.core.governance.policy;

import com.entloom.crud.core.governance.authorization.AuthorizationFields;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.EnumMap;

/** 一个实体选择一个模板，并绑定关系字段和显式规则差异。 */
public final class EntityPolicyBinding {
    private final Class<?> entityType;
    private final String templateCode;
    private final String source;
    private final Map<String, String> relationFields;
    private final List<EntityPolicyOverride> overrides;
    private final AuthorizationFields fieldLimit;

    public EntityPolicyBinding(Class<?> entityType, String templateCode, String source, Map<String, String> relationFields) {
        this(entityType, templateCode, source, relationFields, Collections.<EntityPolicyOverride>emptyList());
    }

    public EntityPolicyBinding(Class<?> entityType, String templateCode, String source,
                               Map<String, String> relationFields, Collection<EntityPolicyOverride> overrides) {
        this(entityType, templateCode, source, relationFields, overrides, null);
    }

    /** 显式字段上限未列出的用途均关闭，仍受实体元数据约束。 */
    public EntityPolicyBinding(Class<?> entityType, String templateCode, String source,
                               Map<String, String> relationFields, Collection<EntityPolicyOverride> overrides,
                               Map<AuthorizationFields.Use, ? extends Collection<String>> fieldLimit) {
        this.entityType = Objects.requireNonNull(entityType, "绑定实体类型不能为空");
        this.templateCode = PolicyIdentityBranch.requireCode(templateCode, "模板 code");
        this.source = PolicyIdentityBranch.requireCode(source, "绑定来源");
        Map<String, String> copy = new LinkedHashMap<String, String>();
        if (relationFields != null) {
            for (Map.Entry<String, String> entry : relationFields.entrySet()) {
                String role = PolicyIdentityBranch.requireCode(entry.getKey(), "关系角色");
                String field = PolicyIdentityBranch.requireCode(entry.getValue(), "关系字段");
                copy.put(role, field);
            }
        }
        this.relationFields = Collections.unmodifiableMap(copy);
        List<EntityPolicyOverride> changes = new ArrayList<EntityPolicyOverride>();
        if (overrides != null) {
            for (EntityPolicyOverride override : overrides) {
                changes.add(Objects.requireNonNull(override, "显式差异不能为空"));
            }
        }
        this.overrides = Collections.unmodifiableList(changes);
        if (fieldLimit == null) {
            this.fieldLimit = null;
        } else {
            Map<AuthorizationFields.Use, Collection<String>> explicit = new EnumMap<AuthorizationFields.Use, Collection<String>>(AuthorizationFields.Use.class);
            for (AuthorizationFields.Use use : fieldLimit.keySet()) Objects.requireNonNull(use, "字段用途不能为空");
            for (AuthorizationFields.Use use : AuthorizationFields.Use.values()) {
                explicit.put(use, fieldLimit.containsKey(use) ? fieldLimit.get(use) : Collections.<String>emptyList());
            }
            this.fieldLimit = AuthorizationFields.of(explicit);
        }
    }

    public Class<?> getEntityType() {
        return entityType;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public String getSource() {
        return source;
    }

    public Map<String, String> getRelationFields() {
        return relationFields;
    }

    public List<EntityPolicyOverride> getOverrides() {
        return overrides;
    }

    public AuthorizationFields getFieldLimit() { return fieldLimit; }
}

package com.entloom.crud.starter.config;

import com.entloom.crud.annotations.*;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import java.lang.annotation.Annotation;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

/** YAML 只作为声明来源；启动期展开基线与显式身份定制，复用统一授权内核。 */
public final class CrudGrantPolicies {
    private CrudGrantPolicies() { }

    @Getter @Setter
    public static class Policy {
        private List<Grant> grants = new ArrayList<>();
    }

    @Getter @Setter
    public static class Grant {
        private List<String> identities;
        private List<String> actions;
        private EntCrudGrantScope scope;
        private String scopeField;
        private String scopeValue;
        private Boolean inheritFields;
        private List<String> fields;
    }

    public static Map<Class<?>, EntCrudGrant[]> resolve(EntityMetaRegistry entities, CrudProperties properties) {
        Map<Class<?>, EntCrudGrant[]> result = new LinkedHashMap<>();
        Set<String> resources = new HashSet<>();
        for (var meta : entities.getEntityMetas()) {
            String resource = meta.getResourceDescriptor().getResourceCode();
            var entity = meta.getEntityType().getAnnotation(EntCrudEntity.class);
            if (entity == null) continue;
            resources.add(resource);
            Policy policy = properties.getPolicies().get(entity.policy());
            if (policy == null) {
                require(!properties.getPolicyOverrides().containsKey(resource), "实体定制必须选择 YAML 策略: " + resource);
                continue;
            }
            require(meta.getEntityType().getAnnotationsByType(EntCrudGrant.class).length == 0,
                "YAML 策略不能同时声明 EntCrudGrant: " + resource);
            require(policy.grants != null && !policy.grants.isEmpty(), "策略授权不能为空: " + entity.policy());
            Map<String, Grant> branches = new LinkedHashMap<>();
            for (Grant grant : policy.grants) {
                require(grant != null && grant.identities != null && !grant.identities.isEmpty(), "策略身份不能为空: " + entity.policy());
                require(grant.actions != null, "策略必须显式声明 actions: " + entity.policy());
                for (String identity : grant.identities) {
                    require(identity != null && !identity.isBlank() && identity.equals(identity.trim()), "策略身份无效: " + entity.policy());
                    require(branches.putIfAbsent(identity, grant) == null, "策略身份重复: " + entity.policy() + "/" + identity);
                }
            }
            for (var override : properties.getPolicyOverrides().getOrDefault(resource, Map.of()).entrySet()) {
                require(branches.containsKey(override.getKey()), "定制引用未知身份: " + resource + "/" + override.getKey());
                Grant patch = override.getValue();
                require(patch != null && patch.identities == null, "定制身份由键指定: " + resource);
                branches.put(override.getKey(), merge(branches.get(override.getKey()), patch));
            }
            List<EntCrudGrant> declarations = new ArrayList<>();
            for (var branch : branches.entrySet()) declarations.add(declaration(branch.getKey(), branch.getValue()));
            result.put(meta.getEntityType(), declarations.toArray(EntCrudGrant[]::new));
        }
        for (String resource : properties.getPolicyOverrides().keySet()) require(resources.contains(resource), "定制引用未知实体: " + resource);
        return result;
    }

    /** 未声明继承；列表显式替换；actions: [] 撤销该身份全部权限。 */
    private static Grant merge(Grant base, Grant patch) {
        Grant merged = new Grant();
        merged.actions = patch.actions == null ? base.actions : patch.actions;
        merged.scope = patch.scope == null ? base.scope : patch.scope;
        merged.scopeField = patch.scopeField == null ? base.scopeField : patch.scopeField;
        merged.scopeValue = patch.scopeValue == null ? base.scopeValue : patch.scopeValue;
        merged.inheritFields = patch.inheritFields == null ? base.inheritFields : patch.inheritFields;
        merged.fields = patch.fields == null ? base.fields : patch.fields;
        return merged;
    }

    private static EntCrudGrant declaration(String identity, Grant grant) {
        EntCrudGrant.Fields[] fields = grant.fields == null ? new EntCrudGrant.Fields[0] : new EntCrudGrant.Fields[] {
            new EntCrudGrant.Fields() {
                public EntCrudFieldUse[] use() { return new EntCrudFieldUse[] {EntCrudFieldUse.ALL}; }
                public String[] value() { return grant.fields.toArray(String[]::new); }
                public String[] exclude() { return new String[0]; }
                public Class<? extends Annotation> annotationType() { return EntCrudGrant.Fields.class; }
            }
        };
        return new EntCrudGrant() {
            public String[] identities() { return new String[] {identity}; }
            public String[] actions() { return grant.actions.toArray(String[]::new); }
            public EntCrudGrantScope scope() { return grant.scope == null ? EntCrudGrantScope.DEFAULT : grant.scope; }
            public String scopeField() { return grant.scopeField == null ? "" : grant.scopeField; }
            public String scopeValue() { return grant.scopeValue == null ? "" : grant.scopeValue; }
            public boolean inheritFields() { return grant.inheritFields == null ? grant.fields == null : grant.inheritFields; }
            public EntCrudGrant.Fields[] fields() { return fields; }
            public Class<? extends Annotation> annotationType() { return EntCrudGrant.class; }
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new ValidationException(message);
    }
}

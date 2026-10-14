package com.entloom.crud.starter.config;

import com.entloom.crud.annotations.EntCrudPolicy;
import com.entloom.crud.annotations.EntPolicyRelation;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.governance.policy.EntityPolicyBinding;
import com.entloom.crud.core.runtime.meta.EntityMeta;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 提取实体及分类接口的唯一策略选择，后续按模板来源进入统一编译链。 */
final class AnnotatedEntityPolicyBindings {
    private AnnotatedEntityPolicyBindings() { }

    static Map<String, EntityPolicyBinding> resolve(Collection<EntityMeta> metas) {
        Map<String, EntityPolicyBinding> bindings = new LinkedHashMap<>();
        for (EntityMeta meta : metas) {
            Class<?> entityType = meta.getEntityType();
            if (entityType == null) {
                throw new ValidationException("策略实体元数据缺少类型: " + meta.getResourceDescriptor().getResourceCode());
            }
            PolicySelection selection = selectPolicy(entityType);
            if (selection == null) {
                continue;
            }
            Map<String, String> relations = new LinkedHashMap<String, String>();
            for (EntPolicyRelation relation : selection.policy.relations()) {
                String previous = relations.put(relation.role(), relation.field());
                if (previous != null) {
                    throw new ValidationException("策略注解关系角色重复: " + relation.role()
                        + "，实体: " + entityType.getName());
                }
            }
            bindings.put(meta.getResourceDescriptor().getResourceCode(), new EntityPolicyBinding(entityType, selection.policy.template(),
                "@EntCrudPolicy:" + selection.declaringType.getName(), relations));
        }
        return bindings;
    }

    private static PolicySelection selectPolicy(Class<?> entityType) {
        Map<Class<?>, EntCrudPolicy> declarations = new LinkedHashMap<Class<?>, EntCrudPolicy>();
        EntCrudPolicy direct = entityType.getDeclaredAnnotation(EntCrudPolicy.class);
        if (direct != null) {
            declarations.put(entityType, direct);
        }
        Set<Class<?>> visited = new LinkedHashSet<Class<?>>();
        for (Class<?> type = entityType; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Class<?> marker : type.getInterfaces()) {
                collectInterfacePolicies(marker, declarations, visited);
            }
        }
        if (declarations.size() > 1) {
            throw new ValidationException("实体策略选择重复: " + entityType.getName() + "，来源: "
                + declarations.keySet().stream().map(Class::getName).collect(Collectors.joining(" / ")));
        }
        if (declarations.isEmpty()) {
            return null;
        }
        Map.Entry<Class<?>, EntCrudPolicy> entry = declarations.entrySet().iterator().next();
        return new PolicySelection(entry.getKey(), entry.getValue());
    }

    private static void collectInterfacePolicies(Class<?> marker, Map<Class<?>, EntCrudPolicy> declarations,
                                                 Set<Class<?>> visited) {
        if (!visited.add(marker)) {
            return;
        }
        EntCrudPolicy policy = marker.getDeclaredAnnotation(EntCrudPolicy.class);
        if (policy != null) {
            declarations.put(marker, policy);
        }
        for (Class<?> parent : marker.getInterfaces()) {
            collectInterfacePolicies(parent, declarations, visited);
        }
    }

    private static final class PolicySelection {
        private final Class<?> declaringType;
        private final EntCrudPolicy policy;

        private PolicySelection(Class<?> declaringType, EntCrudPolicy policy) {
            this.declaringType = declaringType;
            this.policy = policy;
        }
    }
}

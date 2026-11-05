package com.entloom.crud.starter.config;

import com.entloom.crud.annotations.EntCrudCommandAction;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.core.governance.policy.ScenePolicy;
import com.entloom.crud.core.governance.policy.ScenePolicyKey;
import com.entloom.crud.core.runtime.meta.EntityMetaRegistry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.ListableBeanFactory;

/** 无需实例化 Handler，即可在启动时编译动作入口策略。 */
public final class CrudActionPolicies {
    private CrudActionPolicies() { }

    public static List<ScenePolicy> collect(ListableBeanFactory beans, EntityMetaRegistry entities) {
        List<ScenePolicy> result = new ArrayList<>();
        for (String beanName : beans.getBeanNamesForAnnotation(EntCrudCommandAction.class)) {
            EntCrudCommandAction action = beans.findAnnotationOnBean(beanName, EntCrudCommandAction.class);
            if (action == null) continue;
            var definition = CommandActionDefinitions.resolve(action);
            boolean hasPolicy = !action.capability().isBlank() || action.accessEntries().length > 0 || action.portals().length > 0;
            if (!hasPolicy) continue;
            if (action.capability().isBlank() || action.accessEntries().length == 0) {
                throw new IllegalArgumentException("动作策略必须声明 capability 和 accessEntries: " + beanName);
            }
            var resource = entities.getResourceDescriptor(definition.getEntityClass());
            if (resource == null) throw new IllegalArgumentException("动作引用未注册实体: " + beanName);
            Set<String> portals = new LinkedHashSet<>();
            for (String portal : action.portals()) {
                if (portal.isBlank() || !portals.add(portal.trim().toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("动作 portal 不能为空或重复: " + beanName);
                }
            }
            for (String entry : action.accessEntries()) {
                if (entry.isBlank()) throw new IllegalArgumentException("动作入口不能为空: " + beanName);
                result.add(new ScenePolicy(new ScenePolicyKey(entry, resource.getResourceCode(),
                    CrudOperationKey.of(CommandOperation.ACTION), definition.getScene()), action.capability(),
                    portals));
            }
        }
        return result;
    }
}

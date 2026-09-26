package com.entloom.crud.core.governance.policy;

import com.entloom.crud.annotations.EntCrudAction;
import com.entloom.crud.annotations.EntCrudActions;
import com.entloom.crud.api.enums.CommandOperation;
import com.entloom.crud.api.enums.CrudOperationKey;
import com.entloom.crud.core.exception.ValidationException;
import com.entloom.crud.core.runtime.meta.ResourceDescriptor;
import com.entloom.crud.core.util.RouteKeyFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** 统一解析实体动作声明，供策略注册、执行准入和 Handler 绑定使用。 */
public final class EntityActionDeclarations {
    private EntityActionDeclarations() {
    }

    public static boolean allows(Class<?> entityType, String scene) {
        EntCrudActions actions = entityType == null ? null : entityType.getAnnotation(EntCrudActions.class);
        if (actions == null) return true;
        String expected = RouteKeyFactory.normalizeScene(scene);
        if (expected.isEmpty()) return false;
        for (EntCrudAction action : actions.value()) {
            if (expected.equals(RouteKeyFactory.normalizeScene(action.value()))) return true;
        }
        return false;
    }

    public static List<ScenePolicy> policies(ResourceDescriptor resource) {
        List<ScenePolicy> policies = new ArrayList<ScenePolicy>();
        EntCrudActions actions = resource.getEntityType().getAnnotation(EntCrudActions.class);
        if (actions == null) return policies;
        Map<String, String> names = new LinkedHashMap<String, String>();
        for (EntCrudAction action : actions.value()) {
            String scene = RouteKeyFactory.normalizeScene(require(action.value(), "value", resource));
            String name = require(action.name(), "name", resource);
            String previous = names.putIfAbsent(scene, name);
            if (previous != null && !previous.equals(name)) {
                throw new ValidationException("同一实体动作名称不一致: " + resource.getResourceCode() + "/" + scene);
            }
            for (String portal : action.portals()) require(portal, "portals", resource);
            policies.add(new ScenePolicy(
                new ScenePolicyKey(require(action.accessEntry(), "accessEntry", resource), resource.getResourceCode(),
                    CrudOperationKey.of(CommandOperation.ACTION), scene),
                require(action.capability(), "capability", resource),
                new LinkedHashSet<String>(Arrays.asList(action.portals()))
            ));
        }
        return policies;
    }

    private static String require(String value, String field, ResourceDescriptor resource) {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException("实体动作 " + field + " 不能为空: " + resource.getEntityType().getName());
        }
        return value.trim();
    }
}

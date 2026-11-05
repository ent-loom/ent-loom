package com.entloom.crud.core.governance.policy;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.core.exception.ValidationException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Arrays;

/** 实体动作上限的唯一解析入口；资格编译、映射与场景使用同一份默认约定。 */
public final class CrudEntityActions {
    private final Set<String> defaults;

    public CrudEntityActions(Collection<String> defaults) {
        this.defaults = Collections.unmodifiableSet(checked(defaults, "项目默认动作"));
    }

    public static CrudEntityActions readOnly() {
        return new CrudEntityActions(Collections.singletonList("view"));
    }

    public Set<String> resolve(Class<?> type) {
        EntCrudEntity declaration = type.getAnnotation(EntCrudEntity.class);
        if (declaration == null) return Collections.emptySet();
        String source = type.getName() + "@EntCrudEntity";
        boolean inherited = declaration.actions().length == 1 && EntCrudEntity.INHERIT_ACTIONS.equals(declaration.actions()[0]);
        if (!inherited) {
            require(declaration.addActions().length == 0 && declaration.removeActions().length == 0,
                source + ": actions 不能与 addActions/removeActions 同时声明");
            return Collections.unmodifiableSet(checked(Arrays.asList(declaration.actions()), source + ".actions"));
        }
        Set<String> added = checked(Arrays.asList(declaration.addActions()), source + ".addActions");
        Set<String> removed = checked(Arrays.asList(declaration.removeActions()), source + ".removeActions");
        for (String action : added) {
            require(!removed.contains(action), source + ": 同一动作不能同时增加和删除: " + action);
            require(!defaults.contains(action), source + ": 追加动作已在默认集中: " + action);
        }
        for (String action : removed) require(defaults.contains(action), source + ": 删除动作不在默认集中: " + action);
        Set<String> result = new LinkedHashSet<String>(defaults);
        result.removeAll(removed);
        result.addAll(added);
        return Collections.unmodifiableSet(result);
    }

    private static Set<String> checked(Collection<String> values, String source) {
        require(values != null, source + ": 动作集合不能为空");
        Set<String> result = new LinkedHashSet<String>();
        for (String value : values) {
            require(value != null && !value.trim().isEmpty() && value.equals(value.trim())
                && !EntCrudEntity.INHERIT_ACTIONS.equals(value) && !"*".equals(value), source + ": 动作名称无效: " + value);
            require(result.add(value), source + ": 动作重复: " + value);
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new ValidationException(message);
    }
}

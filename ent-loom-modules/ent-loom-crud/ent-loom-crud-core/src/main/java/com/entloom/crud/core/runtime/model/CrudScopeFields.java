package com.entloom.crud.core.runtime.model;

import com.entloom.crud.annotations.EntCrudEntity;
import com.entloom.crud.core.exception.ValidationException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** 默认归属保护；只处理字段边界，不推导访问资格，不依赖实体注册表或 Spring。 */
public final class CrudScopeFields {
    private final Set<String> defaults;

    public CrudScopeFields(Collection<String> defaults) {
        // 多个身份可共用一个归属字段，默认候选集合允许去重。
        this.defaults = new LinkedHashSet<String>();
        for (String field : defaults) {
            require(valid(field), "全局归属字段无效: " + field);
            this.defaults.add(field);
        }
    }

    public static CrudScopeFields none() { return new CrudScopeFields(Collections.<String>emptySet()); }

    /** persistentFields 只包含非主键的持久化标量，不能将关联对象或主键作为治理字段。 */
    public Set<String> resolve(Class<?> type, Collection<String> persistentFields) {
        EntCrudEntity declaration = type.getAnnotation(EntCrudEntity.class);
        if (declaration == null) return Collections.emptySet();
        String source = type.getName() + "@EntCrudEntity";
        boolean inherited = declaration.scopeFields().length == 1
            && EntCrudEntity.INHERIT_SCOPE_FIELDS.equals(declaration.scopeFields()[0]);
        if (!inherited) {
            Set<String> fields = checked(declaration.scopeFields(), source + ".scopeFields");
            require(persistentFields.containsAll(fields), source + ": 治理字段必须是主键以外的持久化标量: " + fields);
            return Collections.unmodifiableSet(fields);
        }
        Set<String> fields = new LinkedHashSet<String>(defaults);
        fields.retainAll(persistentFields);
        return Collections.unmodifiableSet(fields);
    }

    private static Set<String> checked(String[] values, String source) {
        Set<String> result = new LinkedHashSet<String>();
        for (String value : values) {
            require(valid(value), source + ": 治理字段为空、包含首尾空格或使用保留值");
            require(result.add(value), source + ": 治理字段重复: " + value);
        }
        return result;
    }

    private static boolean valid(String value) {
        return value != null && !value.trim().isEmpty() && value.equals(value.trim())
            && !EntCrudEntity.INHERIT_SCOPE_FIELDS.equals(value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new ValidationException(message);
    }
}

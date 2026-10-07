package com.entloom.crud.core.governance.authorization;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 字段使用能力按用途分离；未列出的用途和字段默认不可用。 */
public final class AuthorizationFields {
    public enum Use {
        /** 返回字段。 */
        RETURN,
        /** 写入字段。 */
        WRITE,
        /** 调用方筛选字段。 */
        FILTER,
        /** 排序字段。 */
        SORT,
        /** 统计字段。 */
        STATS,
        /** 关联展开字段。 */
        EXPAND
    }

    private static final AuthorizationFields NONE = new AuthorizationFields(Collections.emptyMap());
    private final Map<Use, Set<String>> allowed;

    private AuthorizationFields(Map<Use, Set<String>> allowed) {
        this.allowed = allowed;
    }

    public static AuthorizationFields none() { return NONE; }

    public static AuthorizationFields of(Map<Use, ? extends Collection<String>> source) {
        Objects.requireNonNull(source, "字段能力声明不能为空");
        Map<Use, Set<String>> copy = new EnumMap<Use, Set<String>>(Use.class);
        for (Map.Entry<Use, ? extends Collection<String>> entry : source.entrySet()) {
            Use use = Objects.requireNonNull(entry.getKey(), "字段用途不能为空");
            Collection<String> fields = Objects.requireNonNull(entry.getValue(), "字段集合不能为空");
            Set<String> names = new LinkedHashSet<String>();
            for (String field : fields) {
                if (field == null || field.trim().isEmpty() || !field.equals(field.trim())) {
                    throw new IllegalArgumentException("字段名称不能为空或包含首尾空格");
                }
                names.add(field);
            }
            copy.put(use, Collections.unmodifiableSet(names));
        }
        return new AuthorizationFields(Collections.unmodifiableMap(copy));
    }

    public boolean allows(Use use, String field) {
        Set<String> fields = allowed.get(Objects.requireNonNull(use, "字段用途不能为空"));
        return fields != null && fields.contains(field);
    }

    public AuthorizationFields intersect(AuthorizationFields limit) {
        Objects.requireNonNull(limit, "字段静态上限不能为空");
        Map<Use, Collection<String>> narrowed = new EnumMap<Use, Collection<String>>(Use.class);
        for (Map.Entry<Use, Set<String>> entry : allowed.entrySet()) {
            Set<String> ceiling = limit.allowed.get(entry.getKey());
            if (ceiling == null) continue;
            Set<String> fields = new LinkedHashSet<String>(entry.getValue());
            fields.retainAll(ceiling);
            if (!fields.isEmpty()) narrowed.put(entry.getKey(), fields);
        }
        return narrowed.isEmpty() ? NONE : of(narrowed);
    }

    public Map<Use, Set<String>> snapshot() { return allowed; }
}

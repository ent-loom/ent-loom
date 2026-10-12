package com.entloom.crud.core.governance.authorization;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** RETURN 缺省为实体普通字段，FILTER 缺省复用 RETURN；其他用途须显式授权，最终受实体元数据约束。 */
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
    private final boolean defaultReturn;

    private AuthorizationFields(Map<Use, Set<String>> allowed) {
        this(allowed, false);
    }

    private AuthorizationFields(Map<Use, Set<String>> allowed, boolean defaultReturn) {
        this.allowed = allowed;
        this.defaultReturn = defaultReturn;
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
        // 仅在声明阶段展开缺省；显式空集合表示关闭筛选。
        if (!copy.containsKey(Use.FILTER) && copy.containsKey(Use.RETURN)) {
            copy.put(Use.FILTER, copy.get(Use.RETURN));
        }
        return new AuthorizationFields(Collections.unmodifiableMap(copy), !source.containsKey(Use.RETURN));
    }

    /** 在实体静态上限已知后展开缺省；none 与显式空 RETURN 均不会开放字段。 */
    public AuthorizationFields resolveDefaults(AuthorizationFields entityLimit) {
        Objects.requireNonNull(entityLimit, "实体字段上限不能为空");
        if (!defaultReturn) return this;
        Set<String> returned = new LinkedHashSet<String>(entityLimit.allowed.getOrDefault(Use.RETURN,
            Collections.<String>emptySet()));
        returned.removeAll(entityLimit.allowed.getOrDefault(Use.EXPAND, Collections.<String>emptySet()));
        Map<Use, Collection<String>> resolved = new EnumMap<Use, Collection<String>>(Use.class);
        resolved.putAll(allowed);
        resolved.put(Use.RETURN, returned);
        return of(resolved);
    }

    public boolean allows(Use use, String field) {
        Set<String> fields = allowed.get(Objects.requireNonNull(use, "字段用途不能为空"));
        return fields != null && fields.contains(field);
    }

    public AuthorizationFields intersect(AuthorizationFields limit) {
        Objects.requireNonNull(limit, "字段静态上限不能为空");
        Map<Use, Set<String>> narrowed = new EnumMap<Use, Set<String>>(Use.class);
        for (Map.Entry<Use, Set<String>> entry : resolveDefaults(limit).allowed.entrySet()) {
            Set<String> ceiling = limit.allowed.get(entry.getKey());
            Set<String> fields = new LinkedHashSet<String>(entry.getValue());
            fields.retainAll(ceiling == null ? Collections.<String>emptySet() : ceiling);
            // 保留空用途，避免再次构建时将已收窄的 FILTER 从 RETURN 重新开放。
            narrowed.put(entry.getKey(), Collections.unmodifiableSet(fields));
        }
        return narrowed.isEmpty() ? NONE : new AuthorizationFields(Collections.unmodifiableMap(narrowed));
    }

    public Map<Use, Set<String>> snapshot() { return allowed; }
}

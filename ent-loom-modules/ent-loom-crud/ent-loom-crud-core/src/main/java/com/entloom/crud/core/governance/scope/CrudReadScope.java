package com.entloom.crud.core.governance.scope;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 单个实体的读取可见性；仅支持等值及集合条件，所有字段条件取交集。 */
public final class CrudReadScope {
    private final boolean allowed;
    private final Map<String, Object> conditions;

    public CrudReadScope(boolean allowed, Map<String, Object> conditions) {
        this.allowed = allowed;
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        if (conditions != null) {
            for (Map.Entry<String, Object> entry : conditions.entrySet()) {
                if (entry.getKey() == null || entry.getKey().trim().isEmpty()) {
                    throw new IllegalArgumentException("可见性字段不能为空");
                }
                Object value = entry.getValue();
                if (value instanceof Collection<?>) {
                    Collection<?> values = (Collection<?>) value;
                    if (values.isEmpty()) throw new IllegalArgumentException("可见性条件集合不能为空");
                    ArrayList<Object> items = new ArrayList<Object>();
                    for (Object item : values) items.add(scalar(item));
                    value = Collections.unmodifiableList(items);
                } else {
                    value = scalar(value);
                }
                copy.put(entry.getKey(), value);
            }
        }
        this.conditions = Collections.unmodifiableMap(copy);
    }

    public static CrudReadScope allow(Map<String, Object> conditions) {
        return new CrudReadScope(true, conditions);
    }

    public static CrudReadScope deny() {
        return new CrudReadScope(false, Collections.<String, Object>emptyMap());
    }

    public boolean isAllowed() { return allowed; }
    public Map<String, Object> getConditions() { return conditions; }

    private static Object scalar(Object value) {
        if (value instanceof Enum<?>) return ((Enum<?>) value).name();
        if (value instanceof String || value instanceof Boolean
            || value instanceof Byte || value instanceof Short || value instanceof Integer
            || value instanceof Long || value instanceof Float || value instanceof Double
            || value instanceof java.math.BigDecimal || value instanceof java.math.BigInteger) return value;
        throw new IllegalArgumentException("可见性条件必须是非空字符串、布尔值、数字或枚举");
    }
}

package com.entloom.crud.core.governance.authorization;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 项目默认授权；归属字段只决定范围，wildcardIdentities 显式授予全局 * 操作与全量范围。 */
public final class CrudGrantDefaults {
    private final Map<String, String> ownerFields;
    private final Set<String> wildcardIdentities;

    /** 第二个参数中的身份默认获得各声明式 CRUD 实体的全部已声明操作，实体同身份声明整体覆盖。 */
    public CrudGrantDefaults(Map<String, String> ownerFields, Set<String> wildcardIdentities) {
        Map<String, String> fields = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : ownerFields.entrySet()) {
            requireCode(entry.getKey());
            requireCode(entry.getValue());
            fields.put(entry.getKey(), entry.getValue());
        }
        Set<String> full = new LinkedHashSet<String>();
        for (String identity : wildcardIdentities) {
            requireCode(identity);
            if (fields.containsKey(identity)) throw new IllegalArgumentException("身份不能同时声明归属字段和全局 * 权限: " + identity);
            full.add(identity);
        }
        this.ownerFields = Collections.unmodifiableMap(fields);
        this.wildcardIdentities = Collections.unmodifiableSet(full);
    }

    public static CrudGrantDefaults none() {
        return new CrudGrantDefaults(Collections.<String, String>emptyMap(), Collections.<String>emptySet());
    }

    public Map<String, String> getOwnerFields() { return ownerFields; }
    public Set<String> getWildcardIdentities() { return wildcardIdentities; }

    private static void requireCode(String value) {
        if (value == null || value.trim().isEmpty() || !value.equals(value.trim()) || "public".equals(value)) {
            throw new IllegalArgumentException("身份及归属字段不能为空、含首尾空格或使用保留值 public");
        }
    }
}

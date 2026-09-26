package com.entloom.crud.core.governance.scope;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

/**
 * 数据范围模型。
 */
@Getter
public class CrudDataScope {
    /** 是否显式表示全量范围。 */
    private final boolean explicitAll;
    /** 范围维度映射。 */
    private final Map<String, Object> dimensions;
    /** 按实体完整类名保存的读取约束，不向其他实体传播。 */
    private final Map<String, CrudReadScope> readScopes;

    public CrudDataScope(boolean explicitAll, Map<String, Object> dimensions) {
        this(explicitAll, dimensions, Collections.<String, CrudReadScope>emptyMap());
    }

    public CrudDataScope(boolean explicitAll, Map<String, Object> dimensions, Map<String, CrudReadScope> readScopes) {
        this.explicitAll = explicitAll;
        this.dimensions = dimensions == null ? new HashMap<String, Object>() : new HashMap<String, Object>(dimensions);
        Map<String, CrudReadScope> copy = new java.util.LinkedHashMap<String, CrudReadScope>();
        if (readScopes != null) {
            for (Map.Entry<String, CrudReadScope> entry : readScopes.entrySet()) {
                if (entry.getKey() == null || entry.getKey().trim().isEmpty() || entry.getValue() == null) {
                    throw new IllegalArgumentException("实体读取范围的名称和约束不能为空");
                }
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        this.readScopes = Collections.unmodifiableMap(copy);
    }

    public CrudDataScope withReadScopes(Map<String, CrudReadScope> scopes) {
        return new CrudDataScope(explicitAll, dimensions, scopes);
    }

    public CrudReadScope readScope(Class<?> entityType) {
        return entityType == null ? null : readScopes.get(entityType.getName());
    }

    public static CrudDataScope allowAll() {
        return new CrudDataScope(true, Collections.<String, Object>emptyMap());
    }

    public static CrudDataScope scoped(Map<String, Object> dimensions) {
        return new CrudDataScope(false, dimensions);
    }

    public Map<String, Object> getDimensions() {
        return Collections.unmodifiableMap(dimensions);
    }
}

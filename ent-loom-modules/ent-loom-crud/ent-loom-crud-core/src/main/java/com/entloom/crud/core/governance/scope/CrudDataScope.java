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

    public CrudDataScope(boolean explicitAll, Map<String, Object> dimensions) {
        this.explicitAll = explicitAll;
        this.dimensions = dimensions == null ? new HashMap<String, Object>() : new HashMap<String, Object>(dimensions);
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

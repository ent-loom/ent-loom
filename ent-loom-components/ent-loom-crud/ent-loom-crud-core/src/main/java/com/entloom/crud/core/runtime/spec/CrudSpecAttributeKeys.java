package com.entloom.crud.core.runtime.spec;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 框架内置 Spec 属性键。
 */
public final class CrudSpecAttributeKeys {
    /** 已解析的数据范围。 */
    public static final String CRUD_DATA_SCOPE = "crudDataScope";
    /** 数据范围维度。 */
    public static final String CRUD_DATA_SCOPE_DIMENSIONS = "crudDataScopeDimensions";
    /** 显式全量范围标记。 */
    public static final String CRUD_EXPLICIT_ALL = "crudExplicitAll";

    /** 框架保留治理键。 */
    public static final Set<String> RESERVED_GOVERNANCE_KEYS = Collections.unmodifiableSet(
        new LinkedHashSet<String>(Arrays.asList(
            CRUD_DATA_SCOPE,
            CRUD_DATA_SCOPE_DIMENSIONS,
            CRUD_EXPLICIT_ALL
        ))
    );

    private CrudSpecAttributeKeys() {
    }
}

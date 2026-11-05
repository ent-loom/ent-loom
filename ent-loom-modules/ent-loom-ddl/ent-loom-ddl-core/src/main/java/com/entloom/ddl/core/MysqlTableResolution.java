package com.entloom.ddl.core;

import com.entloom.ddl.api.DdlCharacterSetMetadata;
import com.entloom.ddl.api.DdlFieldMetadata;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 单表不可变目标类型结果，在 CREATE、Diff、ALTER 之间传递，避免重复推导。 */
public final class MysqlTableResolution {
    private final Map<String, MysqlColumnType> columns;
    private final DdlCharacterSetMetadata defaultCharacterSet;
    private final List<String> warnings;

    MysqlTableResolution(Map<String, MysqlColumnType> columns, DdlCharacterSetMetadata defaultCharacterSet,
                         List<String> warnings) {
        this.columns = Collections.unmodifiableMap(new LinkedHashMap<String, MysqlColumnType>(columns));
        this.defaultCharacterSet = defaultCharacterSet;
        this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
    }

    public MysqlColumnType column(DdlFieldMetadata field) {
        MysqlColumnType result = columns.get(field.fieldName());
        if (result == null) { throw new IllegalArgumentException("字段未参与类型解析: " + field.fieldName()); }
        return result;
    }

    public DdlCharacterSetMetadata defaultCharacterSet() { return defaultCharacterSet; }
    public List<String> warnings() { return warnings; }
}

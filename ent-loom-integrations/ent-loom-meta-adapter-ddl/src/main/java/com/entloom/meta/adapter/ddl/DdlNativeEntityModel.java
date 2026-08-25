package com.entloom.meta.adapter.ddl;

import com.entloom.ddl.enums.DdlTableSize;
import com.entloom.meta.contract.value.SourcedValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DdlNativeEntityModel {
    private final SourcedValue<String> tableName;
    private final SourcedValue<String> schema;
    private final SourcedValue<String> comment;
    private final SourcedValue<DdlTableSize> tableSize;
    private final Map<String, DdlNativeFieldModel> fields;
    private final List<DdlNativeIndexModel> indexes;

    DdlNativeEntityModel(
        SourcedValue<String> tableName,
        SourcedValue<String> schema,
        SourcedValue<String> comment,
        SourcedValue<DdlTableSize> tableSize,
        Map<String, DdlNativeFieldModel> fields,
        List<DdlNativeIndexModel> indexes
    ) {
        this.tableName = tableName;
        this.schema = schema;
        this.comment = comment;
        this.tableSize = tableSize;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<String, DdlNativeFieldModel>(fields));
        this.indexes = Collections.unmodifiableList(new ArrayList<DdlNativeIndexModel>(indexes));
    }

    SourcedValue<String> tableName() {
        return tableName;
    }

    SourcedValue<String> schema() {
        return schema;
    }

    SourcedValue<String> comment() {
        return comment;
    }

    SourcedValue<DdlTableSize> tableSize() {
        return tableSize;
    }

    Map<String, DdlNativeFieldModel> fields() {
        return fields;
    }

    List<DdlNativeIndexModel> indexes() {
        return indexes;
    }
}

package com.entloom.ddl.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DDL 执行请求。
 */
public final class DdlExecutionRequest {
    private final String schema;
    private final boolean createDatabaseIfMissing;
    private final DdlExecutionMode mode;
    private final List<DdlEntityMetadata> entities;

    public DdlExecutionRequest(String schema,
                               boolean createDatabaseIfMissing,
                               DdlExecutionMode mode,
                               List<DdlEntityMetadata> entities) {
        this.schema = schema == null ? "" : schema.trim();
        this.createDatabaseIfMissing = createDatabaseIfMissing;
        this.mode = mode == null ? DdlExecutionMode.NONE : mode;
        this.entities = immutableCopy(entities);
    }

    public String schema() {
        return schema;
    }

    public boolean createDatabaseIfMissing() {
        return createDatabaseIfMissing;
    }

    public DdlExecutionMode mode() {
        return mode;
    }

    public List<DdlEntityMetadata> entities() {
        return entities;
    }

    private static List<DdlEntityMetadata> immutableCopy(List<DdlEntityMetadata> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<DdlEntityMetadata>(source));
    }
}

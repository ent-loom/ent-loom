package com.entloom.meta.starter;

import com.entloom.ddl.api.DdlEntityMetadata;
import com.entloom.ddl.api.MetadataLoadRequest;
import com.entloom.ddl.api.MetadataLoader;
import com.entloom.doc.core.spi.DocIndexProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 文档复用已编译的 DDL 索引。 */
final class DeclaredDdlDocIndexProvider implements DocIndexProvider {
    private final MetadataLoader loader;
    private volatile List<DdlEntityMetadata> models;

    DeclaredDdlDocIndexProvider(MetadataLoader loader) { this.loader = loader; }

    private List<DdlEntityMetadata> models() {
        List<DdlEntityMetadata> loaded = models;
        if (loaded == null) synchronized (this) {
            loaded = models;
            if (loaded == null) {
                loaded = Collections.unmodifiableList(new ArrayList<>(loader.load(new MetadataLoadRequest(Collections.emptyList(), Collections.emptyList()))));
                models = loaded;
            }
        }
        return loaded;
    }

    @Override
    public List<Map<String, Object>> queryIndexes(String tableName) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DdlEntityMetadata model : models()) {
            if (!model.tableName().equals(tableName)) continue;
            model.indexes().forEach(index -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("name", index.name());
                value.put("fields", index.fields());
                value.put("unique", index.unique());
                result.add(Collections.unmodifiableMap(value));
            });
        }
        return Collections.unmodifiableList(result);
    }
}

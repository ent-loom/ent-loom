package com.entloom.ddl.api;

import java.util.List;

/**
 * 元数据加载器。
 */
public interface MetadataLoader {
    List<DdlEntityMetadata> load(MetadataLoadRequest request);
}

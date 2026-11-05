package com.entloom.ddl.api;

import java.util.List;

/**
 * 元数据加载器。
 */
public interface MetadataLoader {
    List<DdlEntityMetadata> load(MetadataLoadRequest request);

    /** 启动期以完整实体范围校验声明；普通 load 允许仅加载请求子集。 */
    default void validateDeclarations(MetadataLoadRequest request) {
        load(request);
    }
}

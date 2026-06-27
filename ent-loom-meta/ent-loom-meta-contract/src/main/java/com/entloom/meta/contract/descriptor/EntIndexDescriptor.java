package com.entloom.meta.contract.descriptor;

import java.util.List;

/**
 * 解析后的通用索引语义描述。
 */
public interface EntIndexDescriptor extends SourcedDescriptor {
    /**
     * 索引名。
     */
    String indexName();

    /**
     * 索引名。
     */
    default String name() {
        return indexName();
    }

    /**
     * 索引字段名。
     */
    List<String> fields();

    /**
     * 是否唯一索引。
     */
    boolean unique();
}

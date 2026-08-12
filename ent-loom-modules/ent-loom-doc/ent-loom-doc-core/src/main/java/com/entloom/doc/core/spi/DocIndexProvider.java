package com.entloom.doc.core.spi;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 索引元数据提供 SPI。
 */
public interface DocIndexProvider {

    /**
     * 查询指定表的索引信息。
     */
    List<Map<String, Object>> queryIndexes(String tableName);

    static DocIndexProvider noop() {
        return new DocIndexProvider() {
            @Override
            public List<Map<String, Object>> queryIndexes(String tableName) {
                return Collections.emptyList();
            }
        };
    }
}

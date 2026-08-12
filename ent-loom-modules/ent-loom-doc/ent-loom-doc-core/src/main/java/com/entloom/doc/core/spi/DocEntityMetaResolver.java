package com.entloom.doc.core.spi;

/**
 * 文档元数据解析 SPI。
 */
public interface DocEntityMetaResolver {

    /**
     * 解析实体表名。
     */
    String resolveTableName(Class<?> entityClass, String configuredTableName);

    /**
     * 解析实体字段映射列名。
     */
    String resolveColumn(Class<?> entityClass, String property);
}

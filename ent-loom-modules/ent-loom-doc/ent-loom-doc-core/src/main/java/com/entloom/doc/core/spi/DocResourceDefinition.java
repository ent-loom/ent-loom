package com.entloom.doc.core.spi;

/**
 * 文档资源定义（由业务侧适配提供）。
 */
public interface DocResourceDefinition {

    /**
     * 资源编码（通常为实体类名）。
     */
    String getResourceCode();

    /**
     * 实体类型。
     */
    Class<?> getEntityClass();

    /**
     * 表名（可为空，由元数据解析器兜底）。
     */
    String getTableName();
}

package com.entloom.crud.core.runtime.engine;

/**
 * 默认引擎可声明的细粒度能力。
 */
public enum EngineFeature {
    /** 根实体字段过滤。 */
    ROOT_FILTER,
    /** 关联路径过滤。 */
    RELATION_FILTER,
    /** 根实体字段排序。 */
    ROOT_SORT,
    /** 关联路径排序。 */
    RELATION_SORT,
    /** 显式字段投影。 */
    SELECT_FIELDS,
    /** 关联展开补数。 */
    RELATION_EXPAND,
    /** 治理数据范围谓词。 */
    GOVERNANCE_SCOPE,
    /** 逻辑删除谓词。 */
    LOGIC_DELETE,
    /** 默认排序。 */
    DEFAULT_SORT,
    /** 通过 id 选择写入目标。 */
    ID_TARGET_WRITE,
    /** 通过 targetFilters 选择写入目标。 */
    TARGET_FILTER_WRITE,
    /** 批量命令。 */
    BATCH_COMMAND,
    /** 统计查询。 */
    STATS_QUERY,
    /** 数据导入执行。 */
    IMPORT_EXECUTION,
    /** 数据导出执行。 */
    EXPORT_EXECUTION,
    /** 场景路由。 */
    SCENE_ROUTE
}

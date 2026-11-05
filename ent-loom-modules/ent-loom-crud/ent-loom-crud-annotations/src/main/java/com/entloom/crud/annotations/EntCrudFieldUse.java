package com.entloom.crud.annotations;

/** 授权字段用途，各用途独立声明。 */
public enum EntCrudFieldUse {
    /** 对全部用途统一收窄，不能与其他用途混写。 */
    ALL,
    /** 返回。 */
    RETURN,
    /** 写入。 */
    WRITE,
    /** 筛选。 */
    FILTER,
    /** 排序。 */
    SORT,
    /** 统计。 */
    STATS,
    /** 关联逐字段投影。 */
    EXPAND
}
